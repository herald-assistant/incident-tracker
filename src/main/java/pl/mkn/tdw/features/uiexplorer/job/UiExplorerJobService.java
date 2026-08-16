package pl.mkn.tdw.features.uiexplorer.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.features.uiexplorer.job.state.UiExplorerJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UiExplorerJobService {

    private final Map<String, UiExplorerJobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, UiExplorerSourceContextSnapshot> sourceContexts = new ConcurrentHashMap<>();
    private final Map<String, UiExplorerPromptPreparation> promptPreparations = new ConcurrentHashMap<>();
    private final UiExplorerSourceContextService sourceContextService;
    private final UiExplorerSourceContextEvidenceMapper sourceContextEvidenceMapper;
    private final UiExplorerPromptPreparationService promptPreparationService;
    private final UiExplorerPromptPreparationEvidenceMapper promptPreparationEvidenceMapper;
    private final UiExplorerAnalysisProvider analysisProvider;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final UiExplorerLocalRunPersistence localRunPersistence;

    public UiExplorerJobStateSnapshot startJob(UiExplorerJobStartRequest request) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        var jobId = UUID.randomUUID().toString();
        var job = new UiExplorerJobState(jobId, request);
        jobs.put(jobId, job);
        var acceptedSnapshot = job.snapshot();
        try {
            applicationTaskExecutor.execute(() -> runJob(jobId, job, request, authRef));
        } catch (RuntimeException exception) {
            job.markFailed(
                    "UI_EXPLORER_JOB_SCHEDULING_FAILED",
                    "UI Explorer analysis could not be scheduled."
            );
            log.error("UI Explorer job scheduling failed jobId={} systemId={}",
                    jobId, request.systemId(), exception);
            persistTerminalSnapshot(job);
            return job.snapshot();
        }
        return acceptedSnapshot;
    }

    void runJob(
            String jobId,
            UiExplorerJobState job,
            UiExplorerJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        if (!job.tryStartExecution()) {
            log.warn("Ignored duplicate UI Explorer execution jobId={} systemId={}", jobId, request.systemId());
            return;
        }
        try {
            job.markSourceContextStarted();
            var sourceContext = sourceContextService.buildContext(
                    request.systemId(),
                    request.branch(),
                    request.screenId(),
                    request.sourceRevision(),
                    request.resolvedSectionModes()
            );
            sourceContexts.put(jobId, sourceContext);
            job.markSourceContextCompleted(sourceContext, sourceContextEvidenceMapper.map(sourceContext));

            job.markAiPreparationStarted();
            var promptPreparation = promptPreparationService.prepare(request, sourceContext);
            promptPreparations.put(jobId, promptPreparation);
            job.markAiPreparationCompleted(
                    promptPreparation.prompt(),
                    promptPreparationEvidenceMapper.map(promptPreparation.artifacts())
            );

            job.markAiAnalysisStarted();
            var analysis = analysisProvider.analyze(
                    jobId,
                    request,
                    sourceContext,
                    promptPreparation,
                    authRef,
                    job::markAiToolEvidenceUpdated,
                    job::markAiActivity
            );
            job.markAiAnalysisCompleted(analysis);
            persistTerminalSnapshot(job);
        } catch (UserFacingApplicationException exception) {
            job.markBlocked(exception.code(), exception.getMessage());
            log.warn("UI Explorer job blocked jobId={} systemId={} code={} message={}",
                    jobId, request.systemId(), exception.code(), exception.getMessage());
            persistTerminalSnapshot(job);
        } catch (RuntimeException exception) {
            job.markFailed(
                    "UI_EXPLORER_ANALYSIS_FAILED",
                    "UI Explorer analysis failed unexpectedly. Retry the analysis or inspect server logs."
            );
            log.error("UI Explorer job failed jobId={} systemId={}", jobId, request.systemId(), exception);
            persistTerminalSnapshot(job);
        }
    }

    public UiExplorerJobStateSnapshot getJob(String jobId) {
        return jobOrThrow(jobId).snapshot();
    }

    UiExplorerSourceContextSnapshot sourceContext(String jobId) {
        var normalized = normalize(jobId);
        var sourceContext = sourceContexts.get(normalized);
        if (sourceContext == null) {
            throw new UiExplorerJobNotFoundException(normalized);
        }
        return sourceContext;
    }

    UiExplorerPromptPreparation promptPreparation(String jobId) {
        var normalized = normalize(jobId);
        var preparation = promptPreparations.get(normalized);
        if (preparation == null) {
            throw new UiExplorerJobNotFoundException(normalized);
        }
        return preparation;
    }

    private UiExplorerJobState jobOrThrow(String jobId) {
        var normalized = normalize(jobId);
        var job = jobs.get(normalized);
        if (job == null) {
            throw new UiExplorerJobNotFoundException(normalized);
        }
        return job;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void persistTerminalSnapshot(UiExplorerJobState job) {
        var snapshot = job.snapshot();
        try {
            localRunPersistence.persistTerminalSnapshot(snapshot);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist local UI Explorer run jobId={} status={} reason={}",
                    snapshot.jobId(),
                    snapshot.status(),
                    exception.getMessage()
            );
        }
    }
}
