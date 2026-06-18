package pl.mkn.incidenttracker.features.flowexplorer.job.state;

import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatTurn;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerChatMessageResponse;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStepResponse;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.incidenttracker.features.flowexplorer.job.error.FlowExplorerJobChatUnavailableException;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FlowExplorerJobState {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_COLLECTING_CONTEXT = "COLLECTING_CONTEXT";
    private static final String STATUS_ANALYZING = "ANALYZING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STEP_DETERMINISTIC_CONTEXT = "DETERMINISTIC_CONTEXT";
    private static final String STEP_DETERMINISTIC_CONTEXT_LABEL = "Deterministic endpoint context";
    private static final String STEP_AI_ANALYSIS = "AI_ANALYSIS";
    private static final String STEP_AI_ANALYSIS_LABEL = "AI endpoint documentation";

    private final String jobId;
    private final FlowExplorerJobStartRequest request;
    private final Instant createdAt;
    private final List<FlowExplorerJobStepResponse> steps;
    private final List<AnalysisEvidenceSection> toolEvidenceSections;
    private final List<AnalysisAiActivityEvent> aiActivityEvents;
    private final List<AnalysisAiToolFeedback> toolFeedback;
    private final List<ChatMessageState> chatMessages;

    private String status;
    private String currentStepCode;
    private String currentStepLabel;
    private String errorCode;
    private String errorMessage;
    private Instant updatedAt;
    private Instant completedAt;
    private String preparedPrompt;
    private FlowExplorerContextSnapshot contextSnapshot;
    private FlowExplorerResultResponse result;

    public FlowExplorerJobState(String jobId, FlowExplorerJobStartRequest request) {
        this.jobId = jobId;
        this.request = request;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.steps = new ArrayList<>();
        this.toolEvidenceSections = new ArrayList<>();
        this.aiActivityEvents = new ArrayList<>();
        this.toolFeedback = new ArrayList<>();
        this.chatMessages = new ArrayList<>();
        this.status = "QUEUED";
    }

    public synchronized void markContextStarted() {
        var now = Instant.now();
        status = STATUS_COLLECTING_CONTEXT;
        currentStepCode = STEP_DETERMINISTIC_CONTEXT;
        currentStepLabel = STEP_DETERMINISTIC_CONTEXT_LABEL;
        updatedAt = now;
        replaceStep(new FlowExplorerJobStepResponse(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                "IN_PROGRESS",
                "Backend buduje deterministic endpoint context.",
                now,
                null
        ));
    }

    public synchronized void markAiStarted(FlowExplorerContextSnapshot contextSnapshot, String prompt) {
        var now = Instant.now();
        status = STATUS_ANALYZING;
        currentStepCode = STEP_AI_ANALYSIS;
        currentStepLabel = STEP_AI_ANALYSIS_LABEL;
        updatedAt = now;
        preparedPrompt = prompt;
        this.contextSnapshot = contextSnapshot;
        completeStep(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                "Backend zbudowal deterministic endpoint context i przygotowal prompt."
        );
        replaceStep(new FlowExplorerJobStepResponse(
                STEP_AI_ANALYSIS,
                STEP_AI_ANALYSIS_LABEL,
                "IN_PROGRESS",
                "AI buduje dokumentacje endpointu na podstawie context snapshotu, artefaktow i dozwolonych tools.",
                now,
                null
        ));
    }

    public synchronized void markAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) {
            return;
        }
        if (appendToolFeedback(toolFeedback, section)) {
            touch();
            return;
        }
        upsertSection(toolEvidenceSections, section);
        touch();
    }

    public synchronized void markAiActivity(AnalysisAiActivityEvent event) {
        if (event == null) {
            return;
        }
        aiActivityEvents.add(event);
        touch();
    }

    public synchronized void markAiCompleted(FlowExplorerAiResponse aiResponse, AnalysisAiUsage usage, String prompt) {
        var now = Instant.now();
        status = STATUS_COMPLETED;
        currentStepCode = null;
        currentStepLabel = null;
        updatedAt = now;
        completedAt = now;
        preparedPrompt = prompt;
        completeStep(STEP_AI_ANALYSIS, STEP_AI_ANALYSIS_LABEL, "AI przygotowalo dokumentacje endpointu.");
        result = resultFromAi(aiResponse, usage, prompt);
    }

    public synchronized void markFailed(String errorCode, String errorMessage) {
        var now = Instant.now();
        status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        updatedAt = now;
        completedAt = now;
        if (currentStepCode != null) {
            replaceStep(new FlowExplorerJobStepResponse(
                    currentStepCode,
                    currentStepLabel,
                    STATUS_FAILED,
                    errorMessage,
                    currentStepStartedAt(currentStepCode, now),
                    now
            ));
        }
    }

    public synchronized void markContextCompleted(FlowExplorerContextSnapshot contextSnapshot, String prompt) {
        var now = Instant.now();
        status = STATUS_COMPLETED;
        currentStepCode = STEP_DETERMINISTIC_CONTEXT;
        currentStepLabel = STEP_DETERMINISTIC_CONTEXT_LABEL;
        updatedAt = now;
        completedAt = now;
        preparedPrompt = prompt;
        this.contextSnapshot = contextSnapshot;
        steps.clear();
        steps.add(new FlowExplorerJobStepResponse(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                STATUS_COMPLETED,
                "Backend zbudowal deterministic endpoint context bez uruchamiania AI.",
                createdAt,
                now
        ));
        result = new FlowExplorerResultResponse(
                STATUS_COMPLETED,
                request.systemId(),
                request.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                "Deterministic context zostal zbudowany; AI nie zostalo jeszcze uruchomione.",
                "Flow Explorer pokazuje compact flow manifest przygotowany pod przyszly prompt.",
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().confidence()
                        : "LOW",
                contextSnapshot != null ? contextSnapshot.limitations() : List.of(),
                prompt,
                null,
                null
        );
    }

    public synchronized FlowExplorerFollowUpChatRequest startChatMessage(
            String userMessageId,
            String assistantMessageId,
            String message
    ) {
        if (!STATUS_COMPLETED.equals(status) || result == null) {
            throw new FlowExplorerJobChatUnavailableException(
                    "FLOW_EXPLORER_CHAT_NOT_READY",
                    "Follow-up chat is available only after a completed Flow Explorer job."
            );
        }
        if (hasActiveAssistantMessage()) {
            throw new FlowExplorerJobChatUnavailableException(
                    "FLOW_EXPLORER_CHAT_IN_PROGRESS",
                    "A follow-up response is already in progress for this Flow Explorer job."
            );
        }

        var history = chatHistory();
        var toolSections = continuationToolEvidenceSections();
        var now = Instant.now();
        chatMessages.add(ChatMessageState.completed(
                userMessageId,
                FlowExplorerChatMessageRole.USER,
                message,
                now
        ));
        chatMessages.add(ChatMessageState.inProgress(
                assistantMessageId,
                FlowExplorerChatMessageRole.ASSISTANT,
                now
        ));
        touch();

        return new FlowExplorerFollowUpChatRequest(
                request,
                contextSnapshot,
                result,
                toolSections,
                history,
                message
        );
    }

    public synchronized void markChatToolEvidenceUpdated(String assistantMessageId, AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) {
            return;
        }

        assistantMessage(assistantMessageId).markToolEvidenceUpdated(section);
        touch();
    }

    public synchronized void markChatAiActivity(String assistantMessageId, AnalysisAiActivityEvent event) {
        if (event == null) {
            return;
        }

        assistantMessage(assistantMessageId).markAiActivity(event);
        touch();
    }

    public synchronized void markChatCompleted(String assistantMessageId, String content, String prompt) {
        assistantMessage(assistantMessageId).markCompleted(content, prompt);
        touch();
    }

    public synchronized void markChatFailed(String assistantMessageId, String code, String message) {
        assistantMessage(assistantMessageId).markFailed(code, message);
        touch();
    }

    public synchronized FlowExplorerJobStateSnapshot snapshot() {
        return new FlowExplorerJobStateSnapshot(
                jobId,
                request.systemId(),
                request.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                request.documentationPreset(),
                request.focusAreas(),
                request.aiOptions().model(),
                request.aiOptions().reasoningEffort(),
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                List.copyOf(steps),
                contextSnapshot,
                List.of(),
                List.copyOf(toolEvidenceSections),
                List.copyOf(aiActivityEvents),
                List.copyOf(toolFeedback),
                chatMessages.stream().map(ChatMessageState::snapshot).toList(),
                preparedPrompt,
                result
        );
    }

    private FlowExplorerResultResponse resultFromAi(
            FlowExplorerAiResponse aiResponse,
            AnalysisAiUsage usage,
            String prompt
    ) {
        var response = aiResponse != null
                ? aiResponse
                : FlowExplorerAiResponse.parseFallback("AI response was not available.");
        return new FlowExplorerResultResponse(
                STATUS_COMPLETED,
                request.systemId(),
                endpointId(),
                httpMethod(),
                endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                response.userIntentSummary(),
                response.audienceSummary(),
                response.confidence(),
                response.visibilityLimits(),
                prompt,
                response,
                usage
        );
    }

    private String endpointId() {
        return contextSnapshot != null && contextSnapshot.endpointId() != null
                ? contextSnapshot.endpointId()
                : request.endpointId();
    }

    private String httpMethod() {
        return contextSnapshot != null && contextSnapshot.httpMethod() != null
                ? contextSnapshot.httpMethod()
                : request.httpMethod();
    }

    private String endpointPath() {
        return contextSnapshot != null && contextSnapshot.endpointPath() != null
                ? contextSnapshot.endpointPath()
                : request.endpointPath();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private void completeStep(String code, String label, String message) {
        var now = Instant.now();
        replaceStep(new FlowExplorerJobStepResponse(
                code,
                label,
                STATUS_COMPLETED,
                message,
                currentStepStartedAt(code, createdAt),
                now
        ));
    }

    private Instant currentStepStartedAt(String code, Instant fallback) {
        return steps.stream()
                .filter(step -> code.equals(step.code()))
                .map(FlowExplorerJobStepResponse::startedAt)
                .findFirst()
                .orElse(fallback);
    }

    private void replaceStep(FlowExplorerJobStepResponse nextStep) {
        for (var index = 0; index < steps.size(); index++) {
            if (nextStep.code().equals(steps.get(index).code())) {
                steps.set(index, nextStep);
                return;
            }
        }
        steps.add(nextStep);
    }

    private void upsertSection(List<AnalysisEvidenceSection> sections, AnalysisEvidenceSection section) {
        for (var index = 0; index < sections.size(); index++) {
            var existing = sections.get(index);
            if (sameSection(existing, section)) {
                sections.set(index, section);
                return;
            }
        }
        sections.add(section);
    }

    private static boolean appendToolFeedback(
            List<AnalysisAiToolFeedback> feedbackList,
            AnalysisEvidenceSection section
    ) {
        if (!AnalysisAiToolFeedbackEvidenceMapper.isToolFeedbackSection(section)) {
            return false;
        }

        for (var feedback : AnalysisAiToolFeedbackEvidenceMapper.fromSection(section)) {
            if (feedbackList.stream().noneMatch(existing -> existing.feedbackId().equals(feedback.feedbackId()))) {
                feedbackList.add(feedback);
            }
        }
        return true;
    }

    private boolean hasActiveAssistantMessage() {
        return chatMessages.stream()
                .anyMatch(message -> message.role == FlowExplorerChatMessageRole.ASSISTANT
                        && message.status == FlowExplorerChatMessageStatus.IN_PROGRESS);
    }

    private ChatMessageState assistantMessage(String assistantMessageId) {
        return chatMessages.stream()
                .filter(message -> message.id.equals(assistantMessageId)
                        && message.role == FlowExplorerChatMessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Flow Explorer assistant chat message: "
                        + assistantMessageId));
    }

    private List<FlowExplorerFollowUpChatTurn> chatHistory() {
        return chatMessages.stream()
                .filter(message -> StringUtils.hasText(message.content))
                .filter(message -> message.role == FlowExplorerChatMessageRole.USER
                        || message.status == FlowExplorerChatMessageStatus.COMPLETED)
                .map(message -> new FlowExplorerFollowUpChatTurn(message.role.name().toLowerCase(), message.content))
                .toList();
    }

    private List<AnalysisEvidenceSection> continuationToolEvidenceSections() {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.addAll(toolEvidenceSections);
        for (var message : chatMessages) {
            sections.addAll(message.toolEvidenceSections);
        }
        return List.copyOf(sections);
    }

    private boolean sameSection(AnalysisEvidenceSection left, AnalysisEvidenceSection right) {
        return left != null
                && right != null
                && java.util.Objects.equals(left.provider(), right.provider())
                && java.util.Objects.equals(left.category(), right.category());
    }

    private static final class ChatMessageState {

        private final String id;
        private final FlowExplorerChatMessageRole role;
        private final Instant createdAt;
        private final List<AnalysisEvidenceSection> toolEvidenceSections;
        private final List<AnalysisAiActivityEvent> aiActivityEvents;
        private final List<AnalysisAiToolFeedback> toolFeedback;
        private FlowExplorerChatMessageStatus status;
        private String content;
        private String errorCode;
        private String errorMessage;
        private String prompt;
        private Instant updatedAt;
        private Instant completedAt;

        private ChatMessageState(
                String id,
                FlowExplorerChatMessageRole role,
                FlowExplorerChatMessageStatus status,
                String content,
                Instant createdAt
        ) {
            this.id = id;
            this.role = role;
            this.status = status;
            this.content = content;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.completedAt = status == FlowExplorerChatMessageStatus.COMPLETED ? createdAt : null;
            this.toolEvidenceSections = new ArrayList<>();
            this.aiActivityEvents = new ArrayList<>();
            this.toolFeedback = new ArrayList<>();
        }

        private static ChatMessageState completed(
                String id,
                FlowExplorerChatMessageRole role,
                String content,
                Instant createdAt
        ) {
            return new ChatMessageState(id, role, FlowExplorerChatMessageStatus.COMPLETED, content, createdAt);
        }

        private static ChatMessageState inProgress(
                String id,
                FlowExplorerChatMessageRole role,
                Instant createdAt
        ) {
            return new ChatMessageState(id, role, FlowExplorerChatMessageStatus.IN_PROGRESS, "", createdAt);
        }

        private void markToolEvidenceUpdated(AnalysisEvidenceSection section) {
            if (appendToolFeedback(toolFeedback, section)) {
                updatedAt = Instant.now();
                return;
            }

            upsertSection(toolEvidenceSections, section);
            updatedAt = Instant.now();
        }

        private void markAiActivity(AnalysisAiActivityEvent event) {
            aiActivityEvents.add(event);
            updatedAt = Instant.now();
        }

        private void markCompleted(String content, String prompt) {
            this.status = FlowExplorerChatMessageStatus.COMPLETED;
            this.content = StringUtils.hasText(content) ? content.trim() : "";
            this.prompt = StringUtils.hasText(prompt) ? prompt.trim() : null;
            this.completedAt = Instant.now();
            this.updatedAt = completedAt;
        }

        private void markFailed(String code, String message) {
            this.status = FlowExplorerChatMessageStatus.FAILED;
            this.errorCode = code;
            this.errorMessage = message;
            this.completedAt = Instant.now();
            this.updatedAt = completedAt;
        }

        private FlowExplorerChatMessageResponse snapshot() {
            return new FlowExplorerChatMessageResponse(
                    id,
                    role.name(),
                    status.name(),
                    content,
                    errorCode,
                    errorMessage,
                    createdAt,
                    updatedAt,
                    completedAt,
                    List.copyOf(toolEvidenceSections),
                    List.copyOf(aiActivityEvents),
                    List.copyOf(toolFeedback),
                    prompt
            );
        }

        private static void upsertSection(List<AnalysisEvidenceSection> sections, AnalysisEvidenceSection candidate) {
            for (var index = 0; index < sections.size(); index++) {
                var current = sections.get(index);
                if (current.provider().equals(candidate.provider()) && current.category().equals(candidate.category())) {
                    sections.set(index, candidate);
                    return;
                }
            }

            sections.add(candidate);
        }
    }
}
