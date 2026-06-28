package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatResponse;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerResultUpdateApplicator;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerSectionModeRequest;
import pl.mkn.tdw.features.flowexplorer.job.export.FlowExplorerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatResult;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunResultUpdateHandler;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FlowExplorerLocalRunChatHandler implements LocalAnalysisRunChatHandler, LocalAnalysisRunResultUpdateHandler {

    static final String FEATURE = "flow-explorer";

    private static final String COMPLETED = "COMPLETED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String USER = "USER";
    private static final String ASSISTANT = "ASSISTANT";

    private final ObjectMapper objectMapper;
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final FlowExplorerFollowUpChatResponseParser followUpResponseParser;
    private final FlowExplorerResultUpdateApplicator resultUpdateApplicator;
    private final FlowExplorerPromptPreparationService promptPreparationService;

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
        var userMessage = StringUtils.hasText(message) ? message.trim() : "";
        var envelope = exportEnvelope(record);
        var snapshot = validatedSnapshot(indexEntry, record.continuation(), envelope);
        var authRef = authRef(record.continuation());

        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var startedAt = Instant.now();
        var promptPreparation = promptPreparationService.prepareFollowUp(startRequest(snapshot), userMessage);
        var captured = new CapturedAssistantState();
        var response = executeChat(
                snapshot,
                record.continuation(),
                promptPreparation,
                authRef,
                assistantMessageId,
                captured
        );
        var parsedResponse = parseFollowUpResponse(response);
        var completedAt = Instant.now();
        var updatedSnapshot = appendCompletedChat(
                snapshot,
                userMessageId,
                assistantMessageId,
                userMessage,
                parsedResponse.message(),
                resultUpdateForResponse(snapshot, parsedResponse),
                captured,
                startedAt,
                completedAt
        );
        var updatedEnvelope = FlowExplorerExportEnvelope.from(updatedSnapshot, completedAt);
        var updatedRecord = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(updatedEnvelope),
                continuationAfterChat(record.continuation(), response)
        );
        return new LocalAnalysisRunChatResult(updatedRecord, completedAt);
    }

    @Override
    public LocalAnalysisRunChatResult applyResultUpdate(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String messageId,
            JsonNode aiResponse
    ) {
        return decideResultUpdate(indexEntry, record, messageId, aiResponse, ResultUpdateDecision.APPLY);
    }

    @Override
    public LocalAnalysisRunChatResult rejectResultUpdate(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String messageId,
            JsonNode aiResponse
    ) {
        return decideResultUpdate(indexEntry, record, messageId, aiResponse, ResultUpdateDecision.REJECT);
    }

    private LocalAnalysisRunChatResult decideResultUpdate(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String messageId,
            JsonNode aiResponse,
            ResultUpdateDecision decision
    ) {
        var envelope = exportEnvelope(record);
        var snapshot = validatedSnapshot(indexEntry, record.continuation(), envelope);
        var assistantMessage = resultUpdateMessage(snapshot, messageId);
        var authoritativeResult = authoritativeResult(snapshot, aiResponse);
        var authRef = authRef(record.continuation());

        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var syncResult = executeResultUpdateSync(
                snapshot,
                record.continuation(),
                authRef,
                decision,
                assistantMessage.id(),
                authoritativeResult
        );
        var completedAt = Instant.now();
        var updatedSnapshot = snapshotAfterResultUpdateDecision(
                snapshot,
                assistantMessage.id(),
                authoritativeResult,
                completedAt
        );
        var updatedEnvelope = FlowExplorerExportEnvelope.from(updatedSnapshot, completedAt);
        var updatedRecord = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(updatedEnvelope),
                continuationAfterChat(record.continuation(), syncResult)
        );
        return new LocalAnalysisRunChatResult(updatedRecord, completedAt);
    }

    private LocalAnalysisRunContinuation continuationAfterChat(
            LocalAnalysisRunContinuation continuation,
            CopilotExecutionResult response
    ) {
        if (continuation == null) {
            return null;
        }

        return continuation.withLatestCopilotSession(response != null ? response.sessionId() : null);
    }

    private CopilotExecutionResult executeChat(
            FlowExplorerJobStateSnapshot snapshot,
            LocalAnalysisRunContinuation continuation,
            FlowExplorerPromptPreparation promptPreparation,
            AnalysisAiAuthRef authRef,
            String assistantMessageId,
            CapturedAssistantState captured
    ) {
        try {
            var runAssembly = runRequestAssembler.assembleFollowUp(
                    "flow-explorer-follow-up-" + assistantMessageId,
                    startRequest(snapshot),
                    snapshot.contextSnapshot(),
                    promptPreparation,
                    continuation.copilotSessionId(),
                    authRef
            );
            var preparedSession = runPreparationService.prepare(runAssembly.runRequest())
                    .withEvidenceSink(captured::addToolEvidence)
                    .withActivitySink(captured::addActivityEvent);
            return executionGateway.execute(preparedSession);
        } catch (CopilotLocalTokenMissingException
                 | GitHubCopilotAuthRequiredException
                 | GitHubCopilotReauthRequiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local Flow Explorer follow-up failed.",
                    exception
            );
        }
    }

    private CopilotExecutionResult executeResultUpdateSync(
            FlowExplorerJobStateSnapshot snapshot,
            LocalAnalysisRunContinuation continuation,
            AnalysisAiAuthRef authRef,
            ResultUpdateDecision decision,
            String assistantMessageId,
            FlowExplorerAiResponse authoritativeResult
    ) {
        try {
            var promptPreparation = new FlowExplorerPromptPreparation(
                    resultUpdateSyncPrompt(decision, assistantMessageId, authoritativeResult),
                    List.of(),
                    Map.of()
            );
            var runAssembly = runRequestAssembler.assembleFollowUp(
                    "flow-explorer-result-update-" + decision.name().toLowerCase() + "-" + assistantMessageId,
                    startRequest(snapshot),
                    snapshot.contextSnapshot(),
                    promptPreparation,
                    continuation.copilotSessionId(),
                    authRef
            );
            var preparedSession = runPreparationService.prepare(runAssembly.runRequest());
            var executionResult = executionGateway.execute(preparedSession);
            if (!"OK".equalsIgnoreCase(executionResult.content() != null ? executionResult.content().trim() : "")) {
                throw new IllegalStateException("Flow Explorer result update session sync did not return OK.");
            }
            return executionResult;
        } catch (CopilotLocalTokenMissingException
                 | GitHubCopilotAuthRequiredException
                 | GitHubCopilotReauthRequiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local Flow Explorer result update session sync failed.",
                    exception
            );
        }
    }

    private String resultUpdateSyncPrompt(
            ResultUpdateDecision decision,
            String assistantMessageId,
            FlowExplorerAiResponse authoritativeResult
    ) {
        var decisionText = switch (decision) {
            case APPLY -> "operator zaakceptowal resultUpdate";
            case REJECT -> "operator odrzucil resultUpdate";
        };
        return """
                Techniczna wiadomosc synchronizacyjna Flow Explorer. Nie pokazuj jej uzytkownikowi jako tresci merytorycznej.

                Decyzja operatora: %s z assistant message id `%s`.
                Ponizszy `FlowExplorerAiResponse` jest teraz authoritative state aplikacji.

                Authoritative result JSON:
                %s

                Odpowiedz dokladnie jednym slowem:
                OK
                """.formatted(
                decisionText,
                assistantMessageId,
                resultUpdateJson(authoritativeResult)
        );
    }

    private String resultUpdateJson(FlowExplorerAiResponse authoritativeResult) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(authoritativeResult);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Flow Explorer result update cannot be serialized.", exception);
        }
    }

    private FlowExplorerFollowUpChatResponse parseFollowUpResponse(CopilotExecutionResult response) {
        try {
            return followUpResponseParser.parse(response != null ? response.content() : null);
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local Flow Explorer follow-up response could not be parsed.",
                    exception
            );
        }
    }

    private JsonNode resultUpdateForResponse(
            FlowExplorerJobStateSnapshot snapshot,
            FlowExplorerFollowUpChatResponse parsedResponse
    ) {
        if (parsedResponse == null || !parsedResponse.hasResultUpdate()) {
            return null;
        }

        try {
            return objectMapper.valueToTree(resultUpdateApplicator.apply(
                    snapshot.result().aiResponse(),
                    parsedResponse.resultUpdate()
            ));
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local Flow Explorer result update could not be prepared.",
                    exception
            );
        }
    }

    private FlowExplorerExportEnvelope exportEnvelope(LocalAnalysisRunRecord record) {
        try {
            return objectMapper.treeToValue(record.exportEnvelope(), FlowExplorerExportEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local Flow Explorer run export envelope cannot be read.",
                    exception
            );
        }
    }

    private AnalysisChatMessageResponse resultUpdateMessage(
            FlowExplorerJobStateSnapshot snapshot,
            String messageId
    ) {
        var normalizedMessageId = requireMessageId(messageId);
        return safeList(snapshot.chatMessages()).stream()
                .filter(message -> normalizedMessageId.equals(message.id()))
                .filter(message -> ASSISTANT.equals(message.role()))
                .filter(message -> COMPLETED.equals(message.status()))
                .filter(message -> message.resultUpdate() != null && !message.resultUpdate().isNull())
                .findFirst()
                .orElseThrow(() -> LocalAnalysisRunContinuationException.unavailable(
                        "Local Flow Explorer result update is not available for this chat message."
                ));
    }

    private String requireMessageId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local Flow Explorer result update message id is required."
            );
        }
        return messageId.trim();
    }

    private FlowExplorerAiResponse authoritativeResult(
            FlowExplorerJobStateSnapshot snapshot,
            JsonNode aiResponse
    ) {
        if (aiResponse == null || aiResponse.isNull()) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local Flow Explorer result update decision requires aiResponse."
            );
        }

        try {
            return objectMapper.treeToValue(aiResponse, FlowExplorerAiResponse.class)
                    .withRequestContext(snapshot.goal(), snapshot.sectionModes());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local Flow Explorer result update aiResponse cannot be read.",
                    exception
            );
        }
    }

    private FlowExplorerJobStateSnapshot validatedSnapshot(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunContinuation continuation,
            FlowExplorerExportEnvelope envelope
    ) {
        if (!FlowExplorerExportEnvelope.SCHEMA.equals(envelope.schema())
                || envelope.version() != FlowExplorerExportEnvelope.VERSION
                || envelope.payload() == null
                || !FlowExplorerExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                || !FlowExplorerExportEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local Flow Explorer run uses an unsupported export envelope.",
                    null
            );
        }

        var snapshot = envelope.payload().job();
        if (snapshot == null || !indexEntry.analysisId().equals(snapshot.jobId())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local Flow Explorer run snapshot does not match the index entry.",
                    null
            );
        }
        if (continuation == null || !continuation.enabled()) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local Flow Explorer run does not have continuation metadata."
            );
        }
        if (!COMPLETED.equals(snapshot.status()) || snapshot.result() == null) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Follow-up chat is available only for completed local Flow Explorer runs."
            );
        }
        if (!StringUtils.hasText(continuation.copilotSessionId())) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local Flow Explorer run does not have a Copilot session id for continuation."
            );
        }
        if (hasActiveAssistantMessage(snapshot)) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "A follow-up response is already in progress for this local Flow Explorer run."
            );
        }
        return snapshot;
    }

    private FlowExplorerJobStartRequest startRequest(FlowExplorerJobStateSnapshot snapshot) {
        return new FlowExplorerJobStartRequest(
                snapshot.systemId(),
                snapshot.endpointId(),
                snapshot.httpMethod(),
                snapshot.endpointPath(),
                snapshot.branch(),
                snapshot.goal(),
                snapshot.focusAreas(),
                safeList(snapshot.sectionModes()).stream()
                        .map(assignment -> new FlowExplorerSectionModeRequest(assignment.id(), assignment.mode()))
                        .toList(),
                null,
                snapshot.aiModel(),
                snapshot.reasoningEffort()
        );
    }

    private AnalysisAiAuthRef authRef(LocalAnalysisRunContinuation continuation) {
        var authMode = continuation != null ? continuation.authMode() : null;
        if (!StringUtils.hasText(authMode)
                || AnalysisAiAuthRef.MODE_LOCAL_TOKEN.equalsIgnoreCase(authMode.trim())) {
            return AnalysisAiAuthRef.localToken(null);
        }
        if (AnalysisAiAuthRef.MODE_GITHUB_APP.equalsIgnoreCase(authMode.trim())) {
            return new AnalysisAiAuthRef(
                    AnalysisAiAuthRef.PROVIDER_GITHUB,
                    AnalysisAiAuthRef.MODE_GITHUB_APP,
                    continuation.authPrincipalRef(),
                    null,
                    true
            );
        }

        throw LocalAnalysisRunContinuationException.corrupted(
                "Local Flow Explorer run has an unsupported auth mode.",
                null
        );
    }

    private FlowExplorerJobStateSnapshot appendCompletedChat(
            FlowExplorerJobStateSnapshot snapshot,
            String userMessageId,
            String assistantMessageId,
            String message,
            String assistantContent,
            JsonNode resultUpdate,
            CapturedAssistantState captured,
            Instant startedAt,
            Instant completedAt
    ) {
        var chatMessages = new ArrayList<>(safeList(snapshot.chatMessages()));
        chatMessages.add(new AnalysisChatMessageResponse(
                userMessageId,
                USER,
                COMPLETED,
                message,
                null,
                null,
                startedAt,
                startedAt,
                startedAt,
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        ));
        chatMessages.add(new AnalysisChatMessageResponse(
                assistantMessageId,
                ASSISTANT,
                COMPLETED,
                assistantContent,
                null,
                null,
                startedAt,
                completedAt,
                completedAt,
                captured.toolEvidenceSections(),
                captured.aiActivityEvents(),
                captured.toolFeedback(),
                message,
                resultUpdate
        ));

        return new FlowExplorerJobStateSnapshot(
                snapshot.jobId(),
                snapshot.systemId(),
                snapshot.endpointId(),
                snapshot.httpMethod(),
                snapshot.endpointPath(),
                snapshot.branch(),
                snapshot.goal(),
                safeList(snapshot.focusAreas()),
                safeList(snapshot.sectionModes()),
                snapshot.aiModel(),
                snapshot.reasoningEffort(),
                snapshot.status(),
                snapshot.currentStepCode(),
                snapshot.currentStepLabel(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                completedAt,
                snapshot.completedAt(),
                safeList(snapshot.steps()),
                snapshot.contextSnapshot(),
                safeList(snapshot.contextSections()),
                safeList(snapshot.toolEvidenceSections()),
                safeList(snapshot.aiActivityEvents()),
                safeList(snapshot.toolFeedback()),
                chatMessages,
                snapshot.preparedPrompt(),
                snapshot.result()
        );
    }

    private FlowExplorerJobStateSnapshot snapshotAfterResultUpdateDecision(
            FlowExplorerJobStateSnapshot snapshot,
            String assistantMessageId,
            FlowExplorerAiResponse authoritativeResult,
            Instant updatedAt
    ) {
        var chatMessages = safeList(snapshot.chatMessages()).stream()
                .map(message -> assistantMessageId.equals(message.id()) ? clearResultUpdate(message) : message)
                .toList();
        return new FlowExplorerJobStateSnapshot(
                snapshot.jobId(),
                snapshot.systemId(),
                snapshot.endpointId(),
                snapshot.httpMethod(),
                snapshot.endpointPath(),
                snapshot.branch(),
                snapshot.goal(),
                safeList(snapshot.focusAreas()),
                safeList(snapshot.sectionModes()),
                snapshot.aiModel(),
                snapshot.reasoningEffort(),
                snapshot.status(),
                snapshot.currentStepCode(),
                snapshot.currentStepLabel(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                updatedAt,
                snapshot.completedAt(),
                safeList(snapshot.steps()),
                snapshot.contextSnapshot(),
                safeList(snapshot.contextSections()),
                safeList(snapshot.toolEvidenceSections()),
                safeList(snapshot.aiActivityEvents()),
                safeList(snapshot.toolFeedback()),
                chatMessages,
                snapshot.preparedPrompt(),
                resultWithAiResponse(snapshot.result(), authoritativeResult)
        );
    }

    private AnalysisChatMessageResponse clearResultUpdate(AnalysisChatMessageResponse message) {
        return new AnalysisChatMessageResponse(
                message.id(),
                message.role(),
                message.status(),
                message.content(),
                message.errorCode(),
                message.errorMessage(),
                message.createdAt(),
                message.updatedAt(),
                message.completedAt(),
                message.toolEvidenceSections(),
                message.aiActivityEvents(),
                message.toolFeedback(),
                message.prompt(),
                null
        );
    }

    private FlowExplorerResultResponse resultWithAiResponse(
            FlowExplorerResultResponse current,
            FlowExplorerAiResponse aiResponse
    ) {
        return new FlowExplorerResultResponse(
                current.status(),
                current.systemId(),
                current.endpointId(),
                current.httpMethod(),
                current.endpointPath(),
                current.branch(),
                current.goal(),
                current.prompt(),
                aiResponse,
                current.usage()
        );
    }

    private boolean hasActiveAssistantMessage(FlowExplorerJobStateSnapshot snapshot) {
        return safeList(snapshot.chatMessages()).stream()
                .anyMatch(message -> ASSISTANT.equals(message.role())
                        && IN_PROGRESS.equals(message.status()));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private enum ResultUpdateDecision {
        APPLY,
        REJECT
    }

    private static final class CapturedAssistantState {

        private final List<AnalysisEvidenceSection> toolEvidenceSections = new ArrayList<>();
        private final List<AnalysisAiActivityEvent> aiActivityEvents = new ArrayList<>();
        private final List<AnalysisAiToolFeedback> toolFeedback = new ArrayList<>();

        private void addToolEvidence(AnalysisEvidenceSection section) {
            if (section == null || !section.hasItems()) {
                return;
            }
            if (appendToolFeedback(section)) {
                return;
            }

            upsertSection(section);
        }

        private void addActivityEvent(AnalysisAiActivityEvent event) {
            if (event != null) {
                aiActivityEvents.add(event);
            }
        }

        private List<AnalysisEvidenceSection> toolEvidenceSections() {
            return List.copyOf(toolEvidenceSections);
        }

        private List<AnalysisAiActivityEvent> aiActivityEvents() {
            return List.copyOf(aiActivityEvents);
        }

        private List<AnalysisAiToolFeedback> toolFeedback() {
            return List.copyOf(toolFeedback);
        }

        private boolean appendToolFeedback(AnalysisEvidenceSection section) {
            if (!AnalysisAiToolFeedbackEvidenceMapper.isToolFeedbackSection(section)) {
                return false;
            }

            for (var feedback : AnalysisAiToolFeedbackEvidenceMapper.fromSection(section)) {
                if (toolFeedback.stream().noneMatch(existing -> existing.feedbackId().equals(feedback.feedbackId()))) {
                    toolFeedback.add(feedback);
                }
            }
            return true;
        }

        private void upsertSection(AnalysisEvidenceSection candidate) {
            for (int index = 0; index < toolEvidenceSections.size(); index++) {
                var current = toolEvidenceSections.get(index);
                if (current.provider().equals(candidate.provider())
                        && current.category().equals(candidate.category())) {
                    toolEvidenceSections.set(index, candidate);
                    return;
                }
            }

            toolEvidenceSections.add(candidate);
        }
    }
}
