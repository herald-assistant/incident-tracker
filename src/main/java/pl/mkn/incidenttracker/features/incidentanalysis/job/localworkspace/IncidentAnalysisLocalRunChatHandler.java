package pl.mkn.incidenttracker.features.incidentanalysis.job.localworkspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatAnalysisSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatResponse;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatTurn;
import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.job.export.IncidentAnalysisExportEnvelope;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunChatResult;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRef;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.incidenttracker.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IncidentAnalysisLocalRunChatHandler implements LocalAnalysisRunChatHandler {

    static final String FEATURE = "incident-analysis";

    private static final String COMPLETED = "COMPLETED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String USER = "USER";
    private static final String ASSISTANT = "ASSISTANT";

    private final ObjectMapper objectMapper;
    private final AnalysisAiChatProvider analysisAiChatProvider;
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
        var envelope = exportEnvelope(record);
        var snapshot = validatedSnapshot(indexEntry, record.continuation(), envelope);
        var authRef = authRef(record.continuation());
        var chatRequest = chatRequest(snapshot, record.continuation(), message, authRef);

        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var startedAt = Instant.now();
        var captured = new CapturedAssistantState();
        var response = executeChat(chatRequest, captured);
        var completedAt = Instant.now();
        var updatedSnapshot = appendCompletedChat(
                snapshot,
                userMessageId,
                assistantMessageId,
                message,
                response,
                captured,
                startedAt,
                completedAt
        );
        var updatedEnvelope = IncidentAnalysisExportEnvelope.from(updatedSnapshot, completedAt);
        var updatedRecord = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(updatedEnvelope),
                record.continuation()
        );
        return new LocalAnalysisRunChatResult(updatedRecord, completedAt);
    }

    private IncidentAnalysisExportEnvelope exportEnvelope(LocalAnalysisRunRecord record) {
        try {
            return objectMapper.treeToValue(record.exportEnvelope(), IncidentAnalysisExportEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local incident analysis run export envelope cannot be read.",
                    exception
            );
        }
    }

    private AnalysisJobStateSnapshot validatedSnapshot(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunContinuation continuation,
            IncidentAnalysisExportEnvelope envelope
    ) {
        if (!IncidentAnalysisExportEnvelope.SCHEMA.equals(envelope.schema())
                || envelope.version() != IncidentAnalysisExportEnvelope.VERSION
                || envelope.payload() == null
                || !IncidentAnalysisExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local incident analysis run uses an unsupported export envelope.",
                    null
            );
        }

        var snapshot = envelope.payload().job();
        if (snapshot == null || !indexEntry.analysisId().equals(snapshot.analysisId())) {
            throw LocalAnalysisRunContinuationException.corrupted(
                    "Local incident analysis run snapshot does not match the index entry.",
                    null
            );
        }
        if (continuation == null || !continuation.enabled()) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Local incident analysis run does not have continuation metadata."
            );
        }
        if (!COMPLETED.equals(snapshot.status()) || snapshot.result() == null) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "Follow-up chat is available only for completed local incident analysis runs."
            );
        }
        if (hasActiveAssistantMessage(snapshot)) {
            throw LocalAnalysisRunContinuationException.unavailable(
                    "A follow-up response is already in progress for this local incident analysis run."
            );
        }
        return snapshot;
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
                "Local incident analysis run has an unsupported auth mode.",
                null
        );
    }

    private AnalysisAiChatRequest chatRequest(
            AnalysisJobStateSnapshot snapshot,
            LocalAnalysisRunContinuation continuation,
            String message,
            AnalysisAiAuthRef authRef
    ) {
        return new AnalysisAiChatRequest(
                snapshot.correlationId(),
                snapshot.environment(),
                snapshot.gitLabBranch(),
                continuation.gitLabGroup(),
                safeList(snapshot.evidenceSections()),
                continuationToolEvidenceSections(snapshot),
                analysisSnapshot(snapshot.result()),
                chatHistory(snapshot),
                message,
                new AnalysisAiOptions(snapshot.aiModel(), snapshot.reasoningEffort()),
                authRef
        );
    }

    private AnalysisAiChatResponse executeChat(
            AnalysisAiChatRequest request,
            CapturedAssistantState captured
    ) {
        try {
            return analysisAiChatProvider.chat(
                    request,
                    captured::addToolEvidence,
                    captured::addActivityEvent
            );
        } catch (CopilotLocalTokenMissingException
                 | GitHubCopilotAuthRequiredException
                 | GitHubCopilotReauthRequiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw LocalAnalysisRunContinuationException.chatFailed(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local incident analysis follow-up failed.",
                    exception
            );
        }
    }

    private AnalysisJobStateSnapshot appendCompletedChat(
            AnalysisJobStateSnapshot snapshot,
            String userMessageId,
            String assistantMessageId,
            String message,
            AnalysisAiChatResponse response,
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
                response != null ? response.prompt() : null
        ));

        return new AnalysisJobStateSnapshot(
                snapshot.analysisId(),
                snapshot.correlationId(),
                snapshot.aiModel(),
                snapshot.reasoningEffort(),
                snapshot.status(),
                snapshot.currentStepCode(),
                snapshot.currentStepLabel(),
                snapshot.environment(),
                snapshot.gitLabBranch(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                completedAt,
                snapshot.completedAt(),
                safeList(snapshot.steps()),
                safeList(snapshot.evidenceSections()),
                safeList(snapshot.toolEvidenceSections()),
                safeList(snapshot.aiActivityEvents()),
                safeList(snapshot.toolFeedback()),
                chatMessages,
                snapshot.preparedPrompt(),
                snapshot.result()
        );
    }

    private boolean hasActiveAssistantMessage(AnalysisJobStateSnapshot snapshot) {
        return safeList(snapshot.chatMessages()).stream()
                .anyMatch(message -> ASSISTANT.equals(message.role())
                        && IN_PROGRESS.equals(message.status()));
    }

    private List<AnalysisAiChatTurn> chatHistory(AnalysisJobStateSnapshot snapshot) {
        return safeList(snapshot.chatMessages()).stream()
                .filter(message -> StringUtils.hasText(message.content()))
                .filter(message -> USER.equals(message.role()) || COMPLETED.equals(message.status()))
                .map(message -> new AnalysisAiChatTurn(message.role().toLowerCase(), message.content()))
                .toList();
    }

    private List<AnalysisEvidenceSection> continuationToolEvidenceSections(AnalysisJobStateSnapshot snapshot) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.addAll(safeList(snapshot.toolEvidenceSections()));
        for (var message : safeList(snapshot.chatMessages())) {
            sections.addAll(safeList(message.toolEvidenceSections()));
        }
        return List.copyOf(sections);
    }

    private AnalysisAiChatAnalysisSnapshot analysisSnapshot(AnalysisResultResponse result) {
        return new AnalysisAiChatAnalysisSnapshot(
                result.detectedProblem(),
                result.affectedProcess(),
                result.affectedBoundedContext(),
                result.affectedTeam(),
                result.functionalAnalysis(),
                result.technicalAnalysis(),
                result.confidence(),
                result.visibilityLimits()
        );
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
