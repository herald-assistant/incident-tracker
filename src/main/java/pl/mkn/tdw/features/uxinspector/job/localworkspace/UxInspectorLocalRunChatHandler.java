package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionStateAvailability;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.uxinspector.ai.chat.*;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorFollowUpReportProjection;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapping;
import pl.mkn.tdw.features.uxinspector.job.api.*;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.chat.AnalysisChatAssistantCapture;
import pl.mkn.tdw.shared.ai.report.AnalysisReportChangeEvidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UxInspectorLocalRunChatHandler implements LocalAnalysisRunChatHandler {
    private static final String FEATURE = "ux-inspector";

    private final ObjectMapper objectMapper;
    private final UxInspectorTargetResolver targetResolver;
    private final UxInspectorFollowUpPromptService promptService;
    private final UxInspectorFollowUpChatService chatService;
    private final UxInspectorFollowUpReportProjection reportProjection;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final CopilotSessionStateAvailability sessionStateAvailability;

    @Override public String feature() { return FEATURE; }

    @Override
    public boolean canContinue(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
        try {
            var snapshot = snapshot(indexEntry, record);
            if (!eligible(snapshot)) return false;
            var continuation = record.continuation();
            if (continuation != null && continuation.enabled() && StringUtils.hasText(continuation.copilotSessionId())) {
                return true;
            }
            return sessionStateAvailability.exists(legacySessionId(indexEntry.analysisId()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public LocalAnalysisRunChatResult continueRun(LocalAnalysisRunIndexEntry indexEntry,
                                                   LocalAnalysisRunRecord record, String message) {
        var snapshot = recoverInterruptedTurn(snapshot(indexEntry, record));
        if (!eligible(snapshot)) throw LocalAnalysisRunContinuationException.unavailable(
                "Follow-up chat is available only for completed or partial UX Inspector runs.");
        var continuation = effectiveContinuation(indexEntry, record);
        var auth = authRef(continuation);
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(auth));
        var startRequest = toStartRequest(snapshot.request());
        var context = targetResolver.resolve(startRequest.systemId(), startRequest.branch(), startRequest.viewId(),
                startRequest.sourceRevision(), startRequest.capture());
        var assistantId = UUID.randomUUID().toString();
        var startedAt = Instant.now();
        var request = new UxInspectorFollowUpChatRequest(
                "ux-inspector-follow-up-" + assistantId, startRequest, context, message,
                continuation.copilotSessionId(), auth, snapshot.report());
        var capture = new AnalysisChatAssistantCapture();
        var prompt = promptService.prepare(request);
        final pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult response;
        try {
            response = chatService.chat(request, capture::addToolEvidence, capture::addActivity);
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "Local UX Inspector follow-up failed.", exception);
        }
        var mapping = reportProjection.project(snapshot.report(), response.report(), startRequest.capture(),
                context, snapshot.usage());
        var completedAt = Instant.now();
        var updated = appendCompletedChat(snapshot, UUID.randomUUID().toString(), assistantId, message,
                response.content(), prompt, response.usage(), capture, startedAt, completedAt, mapping);
        var updatedRecord = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(UxInspectorExportEnvelope.from(updated, completedAt)),
                continuation.withLatestCopilotSession(response.sessionId()));
        return new LocalAnalysisRunChatResult(updatedRecord, completedAt);
    }

    private UxInspectorJobStateSnapshot snapshot(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
        try {
            var envelope = objectMapper.treeToValue(record.exportEnvelope(), UxInspectorExportEnvelope.class);
            if (envelope == null || !envelope.supported() || envelope.payload() == null
                    || !UxInspectorExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                    || envelope.payload().job() == null
                    || !indexEntry.analysisId().equals(envelope.payload().job().jobId())) {
                throw new IllegalArgumentException("Unsupported UX Inspector envelope.");
            }
            return envelope.payload().job();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw LocalAnalysisRunContinuationException.corrupted("Local UX Inspector run envelope cannot be read.", exception);
        }
    }

    private boolean eligible(UxInspectorJobStateSnapshot snapshot) {
        return snapshot != null && snapshot.report() != null && snapshot.result() != null
                && (snapshot.status() == UxInspectorJobStatus.COMPLETED || snapshot.status() == UxInspectorJobStatus.PARTIAL);
    }

    private LocalAnalysisRunContinuation effectiveContinuation(LocalAnalysisRunIndexEntry indexEntry,
                                                                LocalAnalysisRunRecord record) {
        var value = record.continuation();
        if (value != null && value.enabled() && StringUtils.hasText(value.copilotSessionId())) return value;
        var sessionId = legacySessionId(indexEntry.analysisId());
        if (!sessionStateAvailability.exists(sessionId)) throw LocalAnalysisRunContinuationException.unavailable(
                "The saved UX Inspector Copilot session is no longer available.");
        return new LocalAnalysisRunContinuation(true, null, AnalysisAiAuthRef.MODE_LOCAL_TOKEN, null, sessionId,
                LocalAnalysisRunContinuation.COPILOT_RUNTIME_GITHUB_COPILOT_SDK,
                LocalAnalysisRunContinuation.CONTINUATION_MODE_COPILOT_SESSION);
    }

    private String legacySessionId(String jobId) { return "ux-inspector-" + jobId; }

    private AnalysisAiAuthRef authRef(LocalAnalysisRunContinuation continuation) {
        if (!StringUtils.hasText(continuation.authMode())
                || AnalysisAiAuthRef.MODE_LOCAL_TOKEN.equalsIgnoreCase(continuation.authMode())) return AnalysisAiAuthRef.localToken(null);
        if (AnalysisAiAuthRef.MODE_GITHUB_APP.equalsIgnoreCase(continuation.authMode())) {
            return new AnalysisAiAuthRef(AnalysisAiAuthRef.PROVIDER_GITHUB, AnalysisAiAuthRef.MODE_GITHUB_APP,
                    continuation.authPrincipalRef(), null, true);
        }
        throw LocalAnalysisRunContinuationException.corrupted("Local UX Inspector run has an unsupported auth mode.", null);
    }

    private UxInspectorJobStartRequest toStartRequest(UxInspectorJobRequestSnapshot request) {
        return new UxInspectorJobStartRequest(request.systemId(), request.branch(), request.viewId(),
                request.sourceRevision(), request.question(), request.capture(), request.aiModel(), request.reasoningEffort());
    }

    private UxInspectorJobStateSnapshot recoverInterruptedTurn(UxInspectorJobStateSnapshot snapshot) {
        var interruptedAt = Instant.now();
        var changed = false;
        var messages = new ArrayList<AnalysisChatMessageResponse>();
        for (var message : snapshot.chatMessages()) {
            if ("ASSISTANT".equals(message.role()) && "IN_PROGRESS".equals(message.status())) {
                changed = true;
                messages.add(new AnalysisChatMessageResponse(message.id(), message.role(), "FAILED", message.content(),
                        "UX_INSPECTOR_CHAT_INTERRUPTED",
                        "Odpowiedź została przerwana przez restart backendu. Pytanie nie zostało wysłane ponownie.",
                        message.createdAt(), interruptedAt, interruptedAt, message.toolEvidenceSections(),
                        message.aiActivityEvents(), message.toolFeedback(), message.prompt(), message.usage()));
            } else messages.add(message);
        }
        return changed ? copy(snapshot, interruptedAt, messages) : snapshot;
    }

    private UxInspectorJobStateSnapshot appendCompletedChat(UxInspectorJobStateSnapshot snapshot, String userId,
            String assistantId, String message, String content, String prompt, AnalysisAiUsage usage,
            AnalysisChatAssistantCapture capture, Instant startedAt, Instant completedAt,
            UxInspectorReportMapping mapping) {
        var messages = new ArrayList<>(snapshot.chatMessages());
        messages.add(new AnalysisChatMessageResponse(userId, "USER", "COMPLETED", message.trim(), null, null,
                startedAt, startedAt, startedAt, List.of(), List.of(), List.of(), null, null));
        messages.add(new AnalysisChatMessageResponse(assistantId, "ASSISTANT", "COMPLETED", content, null, null,
                startedAt, completedAt, completedAt,
                AnalysisReportChangeEvidence.append(capture.toolEvidenceSections(), snapshot.report(),
                        mapping != null ? mapping.report() : snapshot.report()), capture.aiActivityEvents(),
                capture.toolFeedback(), prompt, usage));
        return copy(snapshot, completedAt, messages, mapping);
    }

    private UxInspectorJobStateSnapshot copy(UxInspectorJobStateSnapshot snapshot, Instant updatedAt,
                                               List<AnalysisChatMessageResponse> messages) {
        return copy(snapshot, updatedAt, messages, null);
    }

    private UxInspectorJobStateSnapshot copy(UxInspectorJobStateSnapshot snapshot, Instant updatedAt,
            List<AnalysisChatMessageResponse> messages, UxInspectorReportMapping mapping) {
        return new UxInspectorJobStateSnapshot(snapshot.jobId(), snapshot.request(), snapshot.status(),
                snapshot.currentStepCode(), snapshot.currentStepLabel(), snapshot.errorCode(), snapshot.errorMessage(),
                snapshot.createdAt(), updatedAt, snapshot.completedAt(), snapshot.steps(), snapshot.contextSections(),
                snapshot.toolEvidenceSections(), snapshot.aiActivityEvents(), snapshot.toolFeedback(),
                snapshot.preparedPrompt(), mapping != null ? mapping.result() : snapshot.result(),
                mapping != null ? mapping.report() : snapshot.report(), snapshot.usage(), snapshot.sourceRevision(),
                snapshot.outputAvailability(), true, messages,
                new UxInspectorChatAvailability(true, "UX_INSPECTOR_CHAT_AVAILABLE",
                        "Follow-up chat can continue the completed UX Inspector session."));
    }
}
