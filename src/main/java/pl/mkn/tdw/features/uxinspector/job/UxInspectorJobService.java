package pl.mkn.tdw.features.uxinspector.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAnalysisProvider;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparationService;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureNormalizer;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetEvidenceMapper;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver;
import pl.mkn.tdw.features.uxinspector.job.api.*;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.features.uxinspector.job.localworkspace.UxInspectorLocalRunPersistence;
import pl.mkn.tdw.features.uxinspector.job.state.UxInspectorJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UxInspectorJobService {
    private final Map<String, UxInspectorJobState> jobs = new ConcurrentHashMap<>();
    private final UxInspectorCaptureNormalizer captureNormalizer;
    private final UxInspectorAiSelectionValidator aiSelectionValidator;
    private final UxInspectorTargetResolver targetResolver;
    private final UxInspectorTargetEvidenceMapper evidenceMapper;
    private final UxInspectorPromptPreparationService promptPreparationService;
    private final UxInspectorAnalysisProvider analysisProvider;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final UxInspectorLocalRunPersistence localRunPersistence;

    public UxInspectorJobStateSnapshot startJob(UxInspectorJobStartRequest request) {
        var auth = authRefResolver.resolveForCurrentRequest();
        aiSelectionValidator.validate(request.model(), request.reasoningEffort(), auth);
        var normalizedCapture = captureNormalizer.normalize(request.capture());
        var normalizedRequest = new UxInspectorJobStartRequest(request.systemId(), request.branch(), request.viewId(),
                request.sourceRevision(), request.question(), normalizedCapture, request.model(), request.reasoningEffort());
        var id = UUID.randomUUID().toString();
        var state = new UxInspectorJobState(id, normalizedRequest);
        jobs.put(id, state);
        persistRequired(state);
        var accepted = state.snapshot();
        try {
            applicationTaskExecutor.execute(() -> runJob(id, state, normalizedRequest, auth));
        } catch (RuntimeException exception) {
            state.fail("UX_INSPECTOR_JOB_SCHEDULING_FAILED", "UX Inspector analysis could not be scheduled.");
            persist(state);
            log.error("UX Inspector scheduling failed jobId={}", id, exception);
            return state.snapshot();
        }
        return accepted;
    }

    void runJob(String id, UxInspectorJobState state, UxInspectorJobStartRequest request, AnalysisAiAuthRef auth) {
        if (!state.tryStart()) return;
        persist(state);
        try {
            var context = targetResolver.resolve(request.systemId(), request.branch(), request.viewId(),
                    request.sourceRevision(), request.capture());
            state.targetResolved(context, evidenceMapper.map(context));
            persist(state);
            state.preparationStarted();
            persist(state);
            var preparation = promptPreparationService.prepare(request, context);
            state.preparationCompleted(preparation.prompt());
            persist(state);
            state.analysisStarted();
            persist(state);
            var analysis = analysisProvider.analyze(id, request, context, preparation, auth,
                    section -> { state.toolEvidence(section); persist(state); },
                    event -> { state.activity(event); persist(state); });
            state.analysisCompleted(analysis);
            persist(state);
        } catch (UserFacingApplicationException exception) {
            state.block(exception.code(), exception.getMessage());
            persist(state);
            log.warn("UX Inspector job blocked jobId={} code={} message={}", id, exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            state.fail("UX_INSPECTOR_ANALYSIS_FAILED", "UX Inspector analysis failed unexpectedly.");
            persist(state);
            log.error("UX Inspector job failed jobId={}", id, exception);
        }
    }

    public UxInspectorJobStateSnapshot getJob(String jobId) {
        var value = jobs.get(normalize(jobId));
        if (value == null) throw new UxInspectorJobException("UX_INSPECTOR_JOB_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "UX Inspector job was not found.");
        return value.snapshot();
    }

    private void persist(UxInspectorJobState state) {
        try { localRunPersistence.persistRunSnapshot(state.snapshot()); }
        catch (RuntimeException exception) { log.warn("Failed to persist UX Inspector run jobId={} reason={}", state.snapshot().jobId(), exception.getMessage()); }
    }
    private void persistRequired(UxInspectorJobState state) {
        try {
            localRunPersistence.persistRunSnapshot(state.snapshot());
        } catch (RuntimeException exception) {
            jobs.remove(state.snapshot().jobId());
            throw new UxInspectorJobException("UX_INSPECTOR_HISTORY_UNAVAILABLE",
                    UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "UX Inspector could not create the required QUEUED history entry.");
        }
    }
    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : ""; }
}
