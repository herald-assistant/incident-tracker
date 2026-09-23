package pl.mkn.tdw.features.uiexplorer.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpChatService;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpPromptService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContextService;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityEvidenceMapper;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerChatMessageRequest;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.features.uiexplorer.job.state.UiExplorerJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UiExplorerJobService {

    private final Map<String, UiExplorerJobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, UiExplorerScreenReachabilityContext> reachabilityContexts = new ConcurrentHashMap<>();
    private final Map<String, UiExplorerPromptPreparation> promptPreparations = new ConcurrentHashMap<>();
    private final Map<String, AnalysisAiAuthRef> authRefs = new ConcurrentHashMap<>();
    private final UiExplorerScreenReachabilityContextService reachabilityContextService;
    private final UiExplorerScreenReachabilityEvidenceMapper reachabilityEvidenceMapper;
    private final UiExplorerPromptPreparationService promptPreparationService;
    private final UiExplorerPromptPreparationEvidenceMapper promptPreparationEvidenceMapper;
    private final UiExplorerAnalysisProvider analysisProvider;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final UiExplorerLocalRunPersistence localRunPersistence;
    private final UiExplorerFollowUpChatService followUpChatService;
    private final UiExplorerFollowUpPromptService followUpPromptService;
    private final LocalAnalysisRunOperationGuard operationGuard;

    public UiExplorerJobStateSnapshot startJob(UiExplorerJobStartRequest request) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        var jobId = UUID.randomUUID().toString();
        var job = new UiExplorerJobState(jobId, request);
        jobs.put(jobId, job);
        authRefs.put(jobId, authRef);
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
            job.markScreenReachabilityStarted();
            var reachabilityContext = reachabilityContextService.buildContext(
                    request.systemId(),
                    request.branch(),
                    request.screenId(),
                    request.sourceRevision(),
                    request.resolvedSectionModes()
            );
            reachabilityContexts.put(jobId, reachabilityContext);
            job.markScreenReachabilityCompleted(
                    reachabilityContext,
                    reachabilityEvidenceMapper.map(reachabilityContext)
            );

            job.markAiPreparationStarted();
            var promptPreparation = promptPreparationService.prepare(request, reachabilityContext);
            promptPreparations.put(jobId, promptPreparation);
            job.markAiPreparationCompleted(
                    promptPreparation.prompt(),
                    promptPreparationEvidenceMapper.map(promptPreparation.artifacts())
            );

            job.markAiAnalysisStarted();
            var analysis = analysisProvider.analyze(
                    jobId,
                    request,
                    reachabilityContext,
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

    public UiExplorerJobStateSnapshot startChatMessage(String jobId, UiExplorerChatMessageRequest request) {
        var normalized = normalize(jobId);
        var job = jobOrThrow(normalized);
        var context = reachabilityContexts.get(normalized);
        var authRef = authRefs.get(normalized);
        if (context == null || authRef == null) {
            throw new pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobChatUnavailableException(
                    "UI_EXPLORER_CHAT_CONTEXT_UNAVAILABLE",
                    "The UI Explorer run no longer has its live continuation context. Open it from Analysis History."
            );
        }
        var lease = operationGuard.tryAcquire(normalized)
                .orElseThrow(() -> new pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobChatUnavailableException(
                        "UI_EXPLORER_CHAT_IN_PROGRESS", "Another operation is already in progress for this UI Explorer run."));
        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        final String sessionId;
        try {
            sessionId = job.startChatMessage(userMessageId, assistantMessageId, request.message());
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
        var chatRequest = new UiExplorerFollowUpChatRequest(
                "ui-explorer-follow-up-" + assistantMessageId,
                job.initialRequest(), context, request.message(), sessionId, authRef
        );
        persistSnapshot(job, authRef, context);
        try {
            applicationTaskExecutor.execute(() -> runChat(job, assistantMessageId, chatRequest, authRef, context, lease));
        } catch (RuntimeException exception) {
            job.markChatFailed(assistantMessageId, "UI_EXPLORER_CHAT_SCHEDULING_FAILED",
                    "UI Explorer follow-up could not be scheduled.");
            persistSnapshot(job, authRef, context);
            lease.close();
        }
        return job.snapshot();
    }

    private void runChat(
            UiExplorerJobState job,
            String assistantMessageId,
            UiExplorerFollowUpChatRequest request,
            AnalysisAiAuthRef authRef,
            UiExplorerScreenReachabilityContext context,
            LocalAnalysisRunOperationGuard.Lease lease
    ) {
        try {
            var prompt = followUpPromptService.prepare(request);
            var response = followUpChatService.chat(
                    request,
                    section -> job.markChatToolEvidenceUpdated(assistantMessageId, section),
                    event -> job.markChatAiActivity(assistantMessageId, event)
            );
            job.markChatCompleted(assistantMessageId, response.content(), prompt, response.usage(), response.sessionId());
        } catch (RuntimeException exception) {
            log.error("UI Explorer follow-up failed jobId={} message={}", job.snapshot().jobId(), exception.getMessage(), exception);
            job.markChatFailed(assistantMessageId, "UI_EXPLORER_CHAT_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "UI Explorer follow-up failed unexpectedly.");
        } finally {
            persistSnapshot(job, authRef, context);
            lease.close();
        }
    }

    UiExplorerScreenReachabilityContext reachabilityContext(String jobId) {
        var normalized = normalize(jobId);
        var reachabilityContext = reachabilityContexts.get(normalized);
        if (reachabilityContext == null) {
            throw new UiExplorerJobNotFoundException(normalized);
        }
        return reachabilityContext;
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
            var context = reachabilityContexts.get(snapshot.jobId());
            var authRef = authRefs.get(snapshot.jobId());
            if (context != null) {
                localRunPersistence.persistRunSnapshot(snapshot, authRef, job.copilotSessionId(), context);
            } else {
                localRunPersistence.persistTerminalSnapshot(snapshot);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist local UI Explorer run jobId={} status={} reason={}",
                    snapshot.jobId(),
                    snapshot.status(),
                    exception.getMessage()
            );
        }
    }

    private void persistSnapshot(
            UiExplorerJobState job,
            AnalysisAiAuthRef authRef,
            UiExplorerScreenReachabilityContext context
    ) {
        try {
            localRunPersistence.persistRunSnapshot(job.snapshot(), authRef, job.copilotSessionId(), context);
        } catch (RuntimeException exception) {
            log.warn("Failed to persist UI Explorer chat snapshot jobId={} reason={}",
                    job.snapshot().jobId(), exception.getMessage());
        }
    }
}
