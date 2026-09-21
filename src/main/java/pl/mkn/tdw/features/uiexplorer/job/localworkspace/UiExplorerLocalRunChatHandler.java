package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpChatService;
import pl.mkn.tdw.features.uiexplorer.ai.chat.UiExplorerFollowUpPromptService;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerChatAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatResult;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.chat.AnalysisChatAssistantCapture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UiExplorerLocalRunChatHandler implements LocalAnalysisRunChatHandler {

    private static final String FEATURE = "ui-explorer";

    private final ObjectMapper objectMapper;
    private final UiExplorerContinuationSnapshotStore continuationSnapshotStore;
    private final UiExplorerFollowUpPromptService promptService;
    private final UiExplorerFollowUpChatService chatService;
    private final UiExplorerLocalRunSnapshotSanitizer sanitizer;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;

    @Override
    public String feature() {
        return FEATURE;
    }

    @Override
    public LocalAnalysisRunChatResult continueRun(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String message
    ) {
        var envelope = envelope(record);
        var snapshot = validatedSnapshot(indexEntry, record.continuation(), envelope);
        var privateSnapshot = continuationSnapshotStore.findById(indexEntry.analysisId())
                .orElseThrow(() -> LocalAnalysisRunContinuationException.corrupted(
                        "UI Explorer continuation context is missing.", null));
        if (!privateSnapshot.matches(snapshot)) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "UI Explorer continuation context does not match the saved report.", null);
        }
        var authRef = authRef(record.continuation());
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var assistantId = UUID.randomUUID().toString();
        var startedAt = Instant.now();
        var request = new UiExplorerFollowUpChatRequest(
                "ui-explorer-follow-up-" + assistantId,
                privateSnapshot.toStartRequest(),
                privateSnapshot.toContext(),
                snapshot.report(),
                message,
                record.continuation().copilotSessionId(),
                authRef
        );
        var capture = new AnalysisChatAssistantCapture();
        var prompt = promptService.prepare(request);
        var response = execute(request, capture);
        var completedAt = Instant.now();
        var updatedSnapshot = sanitizer.sanitize(appendCompletedChat(
                snapshot, UUID.randomUUID().toString(), assistantId, message,
                response.content(), prompt, response.usage(), capture, startedAt, completedAt));
        var updatedRecord = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(UiExplorerLocalRunEnvelope.from(updatedSnapshot, completedAt)),
                record.continuation().withLatestCopilotSession(response.sessionId())
        );
        return new LocalAnalysisRunChatResult(updatedRecord, completedAt);
    }

    private pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult execute(
            UiExplorerFollowUpChatRequest request,
            AnalysisChatAssistantCapture capture
    ) {
        try {
            return chatService.chat(request, capture::addToolEvidence, capture::addActivity);
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local UI Explorer follow-up failed.",
                    exception
            );
        }
    }

    private UiExplorerLocalRunEnvelope envelope(LocalAnalysisRunRecord record) {
        try {
            return objectMapper.treeToValue(record.exportEnvelope(), UiExplorerLocalRunEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local UI Explorer run envelope cannot be read.", exception);
        }
    }

    private UiExplorerJobStateSnapshot validatedSnapshot(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunContinuation continuation,
            UiExplorerLocalRunEnvelope envelope
    ) {
        if (!UiExplorerLocalRunEnvelope.SCHEMA.equals(envelope.schema())
                || envelope.version() != UiExplorerLocalRunEnvelope.VERSION
                || envelope.payload() == null
                || !UiExplorerLocalRunEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                || !UiExplorerLocalRunEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local UI Explorer run uses an unsupported envelope.", null);
        }
        var snapshot = recoverInterruptedTurn(envelope.payload().job());
        if (snapshot == null || !indexEntry.analysisId().equals(snapshot.jobId())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local UI Explorer run snapshot does not match the index entry.", null);
        }
        if (continuation == null || !continuation.enabled()
                || !StringUtils.hasText(continuation.copilotSessionId())) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local UI Explorer run does not have resumable Copilot session metadata.");
        }
        if (snapshot.result() == null || snapshot.report() == null
                || !("COMPLETED".equals(snapshot.status().name()) || "PARTIAL".equals(snapshot.status().name()))) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Follow-up chat is available only for completed or partial UI Explorer runs.");
        }
        return snapshot;
    }

    private UiExplorerJobStateSnapshot recoverInterruptedTurn(UiExplorerJobStateSnapshot snapshot) {
        var interruptedAt = Instant.now();
        var changed = false;
        var messages = new ArrayList<AnalysisChatMessageResponse>();
        for (var message : snapshot.chatMessages()) {
            if ("ASSISTANT".equals(message.role()) && "IN_PROGRESS".equals(message.status())) {
                changed = true;
                messages.add(new AnalysisChatMessageResponse(
                        message.id(), message.role(), "FAILED", message.content(),
                        "UI_EXPLORER_CHAT_INTERRUPTED",
                        "Odpowiedź została przerwana przez restart backendu. Pytanie nie zostało wysłane ponownie.",
                        message.createdAt(), interruptedAt, interruptedAt,
                        message.toolEvidenceSections(), message.aiActivityEvents(), message.toolFeedback(),
                        message.prompt(), message.usage()));
            } else {
                messages.add(message);
            }
        }
        if (!changed) {
            return snapshot;
        }
        return new UiExplorerJobStateSnapshot(
                snapshot.jobId(), snapshot.request(), snapshot.status(), snapshot.currentStepCode(),
                snapshot.currentStepLabel(), snapshot.errorCode(), snapshot.errorMessage(), snapshot.createdAt(),
                interruptedAt, snapshot.completedAt(), snapshot.steps(), snapshot.contextSections(),
                snapshot.toolEvidenceSections(), snapshot.aiActivityEvents(), snapshot.toolFeedback(),
                snapshot.preparedPrompt(), snapshot.result(), snapshot.report(), snapshot.usage(),
                snapshot.sourceRevision(), snapshot.outputAvailability(), true, messages,
                new UiExplorerChatAvailability(true, null, null));
    }

    private AnalysisAiAuthRef authRef(LocalAnalysisRunContinuation continuation) {
        if (!StringUtils.hasText(continuation.authMode())
                || AnalysisAiAuthRef.MODE_LOCAL_TOKEN.equalsIgnoreCase(continuation.authMode())) {
            return AnalysisAiAuthRef.localToken(null);
        }
        if (AnalysisAiAuthRef.MODE_GITHUB_APP.equalsIgnoreCase(continuation.authMode())) {
            return new AnalysisAiAuthRef(
                    AnalysisAiAuthRef.PROVIDER_GITHUB,
                    AnalysisAiAuthRef.MODE_GITHUB_APP,
                    continuation.authPrincipalRef(),
                    null,
                    true
            );
        }
        throw LocalAnalysisRunContinuationException.corrupted(
                "Local UI Explorer run has an unsupported auth mode.", null);
    }

    private UiExplorerJobStateSnapshot appendCompletedChat(
            UiExplorerJobStateSnapshot snapshot,
            String userId,
            String assistantId,
            String message,
            String content,
            String prompt,
            pl.mkn.tdw.shared.ai.AnalysisAiUsage usage,
            AnalysisChatAssistantCapture capture,
            Instant startedAt,
            Instant completedAt
    ) {
        var messages = new ArrayList<>(snapshot.chatMessages());
        messages.add(new AnalysisChatMessageResponse(
                userId, "USER", "COMPLETED", message.trim(), null, null,
                startedAt, startedAt, startedAt, List.of(), List.of(), List.of(), null, null));
        messages.add(new AnalysisChatMessageResponse(
                assistantId, "ASSISTANT", "COMPLETED", content, null, null,
                startedAt, completedAt, completedAt, capture.toolEvidenceSections(),
                capture.aiActivityEvents(), capture.toolFeedback(), prompt, usage));
        return new UiExplorerJobStateSnapshot(
                snapshot.jobId(), snapshot.request(), snapshot.status(), snapshot.currentStepCode(),
                snapshot.currentStepLabel(), snapshot.errorCode(), snapshot.errorMessage(), snapshot.createdAt(),
                completedAt, snapshot.completedAt(), snapshot.steps(), snapshot.contextSections(),
                snapshot.toolEvidenceSections(), snapshot.aiActivityEvents(), snapshot.toolFeedback(),
                snapshot.preparedPrompt(), snapshot.result(), snapshot.report(), snapshot.usage(),
                snapshot.sourceRevision(), snapshot.outputAvailability(), true, messages,
                new UiExplorerChatAvailability(true, null, null)
        );
    }
}
