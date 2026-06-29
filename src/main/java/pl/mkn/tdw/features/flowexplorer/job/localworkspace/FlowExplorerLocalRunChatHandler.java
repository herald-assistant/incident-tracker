package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerSectionModeRequest;
import pl.mkn.tdw.features.flowexplorer.job.export.FlowExplorerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatResult;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FlowExplorerLocalRunChatHandler implements LocalAnalysisRunChatHandler {

    static final String FEATURE = "flow-explorer";

    private static final String COMPLETED = "COMPLETED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String USER = "USER";
    private static final String ASSISTANT = "ASSISTANT";

    private final ObjectMapper objectMapper;
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler;
    private final FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
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
        var userMessage = StringUtils.hasText(message) ? message.trim() : "";
        var envelope = exportEnvelope(record);
        var snapshot = validatedSnapshot(indexEntry, record.continuation(), envelope);
        var authRef = authRef(record.continuation());

        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var startedAt = Instant.now();
        var promptPreparation = followUpPromptPreparationService.prepare(
                startRequest(snapshot),
                snapshot.contextSnapshot(),
                userMessage
        );
        var captured = new CapturedAssistantState();
        var response = executeChat(
                snapshot,
                record.continuation(),
                promptPreparation,
                authRef,
                assistantMessageId,
                captured
        );
        var completedAt = Instant.now();
        var updatedSnapshot = appendCompletedChat(
                snapshot,
                userMessageId,
                assistantMessageId,
                userMessage,
                promptPreparation.prompt(),
                response,
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
            String assistantPrompt,
            CopilotExecutionResult response,
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
                null
        ));
        chatMessages.add(new AnalysisChatMessageResponse(
                assistantMessageId,
                ASSISTANT,
                COMPLETED,
                response != null ? response.content() : "",
                null,
                null,
                startedAt,
                completedAt,
                completedAt,
                captured.toolEvidenceSections(),
                captured.aiActivityEvents(),
                captured.toolFeedback(),
                assistantPrompt
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

    private boolean hasActiveAssistantMessage(FlowExplorerJobStateSnapshot snapshot) {
        return safeList(snapshot.chatMessages()).stream()
                .anyMatch(message -> ASSISTANT.equals(message.role())
                        && IN_PROGRESS.equals(message.status()));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
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
