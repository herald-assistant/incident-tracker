package pl.mkn.tdw.features.uxinspector.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAnalysisProvider;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparationService;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatRequest;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatService;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpPromptService;
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
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UxInspectorJobService {
    private final Map<String, UxInspectorJobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext> targetContexts = new ConcurrentHashMap<>();
    private final Map<String, AnalysisAiAuthRef> authRefs = new ConcurrentHashMap<>();
    private final UxInspectorCaptureNormalizer captureNormalizer;
    private final UxInspectorAiSelectionValidator aiSelectionValidator;
    private final UxInspectorTargetResolver targetResolver;
    private final UxInspectorTargetEvidenceMapper evidenceMapper;
    private final UxInspectorPromptPreparationService promptPreparationService;
    private final UxInspectorAnalysisProvider analysisProvider;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final UxInspectorLocalRunPersistence localRunPersistence;
    private final UxInspectorFollowUpChatService followUpChatService;
    private final UxInspectorFollowUpPromptService followUpPromptService;
    private final LocalAnalysisRunOperationGuard operationGuard;

    public UxInspectorJobStateSnapshot startJob(UxInspectorJobStartRequest request) {
        var auth = authRefResolver.resolveForCurrentRequest();
        aiSelectionValidator.validate(request.model(), request.reasoningEffort(), auth);
        var normalizedCapture = captureNormalizer.normalize(request.capture());
        var normalizedRequest = new UxInspectorJobStartRequest(request.systemId(), request.branch(), request.viewId(),
                request.sourceRevision(), request.question(), normalizedCapture, request.model(), request.reasoningEffort());
        var id = UUID.randomUUID().toString();
        var state = new UxInspectorJobState(id, normalizedRequest);
        jobs.put(id, state);
        authRefs.put(id, auth);
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
            targetContexts.put(id, context);
            state.targetResolved(context, evidenceMapper.map(context));
            persist(state);
            state.preparationStarted();
            persist(state);
            var preparation = promptPreparationService.prepare(request, context);
            state.preparationCompleted(preparation.prompt(), preparation.artifactContents().size());
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

    public UxInspectorJobStateSnapshot startChatMessage(String jobId, UxInspectorChatMessageRequest request) {
        var normalized = normalize(jobId);
        var state = jobs.get(normalized);
        if (state == null) throw new UxInspectorJobException("UX_INSPECTOR_JOB_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "UX Inspector job was not found.");
        var context = targetContexts.get(normalized);
        var auth = authRefs.get(normalized);
        if (context == null || auth == null) {
            throw new pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobChatUnavailableException(
                    "UX_INSPECTOR_CHAT_CONTEXT_UNAVAILABLE",
                    "The UX Inspector run no longer has its live continuation context. Open it from Analysis History.");
        }
        var lease = operationGuard.tryAcquire(normalized).orElseThrow(() ->
                new pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobChatUnavailableException(
                        "UX_INSPECTOR_CHAT_IN_PROGRESS", "Another operation is already in progress for this UX Inspector run."));
        var userId = UUID.randomUUID().toString();
        var assistantId = UUID.randomUUID().toString();
        final String sessionId;
        try { sessionId = state.startChatMessage(userId, assistantId, request.message()); }
        catch (RuntimeException exception) { lease.close(); throw exception; }
        var chatRequest = new UxInspectorFollowUpChatRequest(
                "ux-inspector-follow-up-" + assistantId, state.initialRequest(), context,
                request.message(), sessionId, auth);
        persist(state);
        try {
            applicationTaskExecutor.execute(() -> runChat(state, assistantId, chatRequest, lease));
        } catch (RuntimeException exception) {
            state.chatFailed(assistantId, "UX_INSPECTOR_CHAT_SCHEDULING_FAILED",
                    "UX Inspector follow-up could not be scheduled.");
            persist(state); lease.close();
        }
        return state.snapshot();
    }

    private void runChat(UxInspectorJobState state, String assistantId,
                         UxInspectorFollowUpChatRequest request, LocalAnalysisRunOperationGuard.Lease lease) {
        try {
            var prompt = followUpPromptService.prepare(request);
            var response = followUpChatService.chat(request,
                    section -> state.chatToolEvidence(assistantId, section),
                    event -> state.chatActivity(assistantId, event));
            state.chatCompleted(assistantId, response.content(), prompt, response.usage(), response.sessionId());
        } catch (RuntimeException exception) {
            log.error("UX Inspector follow-up failed jobId={} message={}", state.snapshot().jobId(), exception.getMessage(), exception);
            state.chatFailed(assistantId, "UX_INSPECTOR_CHAT_FAILED",
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "UX Inspector follow-up failed unexpectedly.");
        } finally {
            persist(state); lease.close();
        }
    }

    private void persist(UxInspectorJobState state) {
        try { localRunPersistence.persistRunSnapshot(state.snapshot(), authRefs.get(state.snapshot().jobId()), state.copilotSessionId()); }
        catch (RuntimeException exception) { log.warn("Failed to persist UX Inspector run jobId={} reason={}", state.snapshot().jobId(), exception.getMessage()); }
    }
    private void persistRequired(UxInspectorJobState state) {
        try {
            localRunPersistence.persistRunSnapshot(state.snapshot(), authRefs.get(state.snapshot().jobId()), state.copilotSessionId());
        } catch (RuntimeException exception) {
            jobs.remove(state.snapshot().jobId());
            throw new UxInspectorJobException("UX_INSPECTOR_HISTORY_UNAVAILABLE",
                    UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "UX Inspector could not create the required QUEUED history entry.");
        }
    }
    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : ""; }
}
