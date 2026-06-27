package pl.mkn.tdw.features.flowexplorer.job.state;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerFollowUpChatTurn;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobChatUnavailableException;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
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
    private static final String PHASE_CONTEXT = "CONTEXT";
    private static final String PHASE_AI = "AI";
    private static final AnalysisEvidenceReference FLOW_CONTEXT_EVIDENCE =
            new AnalysisEvidenceReference("flow-explorer", "endpoint-context");

    private final String jobId;
    private final FlowExplorerJobStartRequest request;
    private final Instant createdAt;
    private final List<AnalysisJobStepResponse> steps;
    private final List<AnalysisEvidenceSection> contextSections;
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
        this.contextSections = new ArrayList<>();
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
        replaceStep(new AnalysisJobStepResponse(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                PHASE_CONTEXT,
                "IN_PROGRESS",
                "Backend buduje deterministic endpoint context.",
                null,
                now,
                null,
                List.of(),
                List.of(FLOW_CONTEXT_EVIDENCE)
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
        replaceContextSections(contextSnapshot);
        completeStep(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                PHASE_CONTEXT,
                "Backend zbudowal deterministic endpoint context i przygotowal prompt.",
                contextItemCount()
        );
        replaceStep(new AnalysisJobStepResponse(
                STEP_AI_ANALYSIS,
                STEP_AI_ANALYSIS_LABEL,
                PHASE_AI,
                "IN_PROGRESS",
                "AI buduje dokumentacje endpointu na podstawie context snapshotu, artefaktow i dozwolonych tools.",
                null,
                now,
                null,
                List.of(FLOW_CONTEXT_EVIDENCE),
                List.of()
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
        completeStep(
                STEP_AI_ANALYSIS,
                STEP_AI_ANALYSIS_LABEL,
                PHASE_AI,
                "AI przygotowalo dokumentacje endpointu.",
                null,
                usage
        );
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
            replaceStep(new AnalysisJobStepResponse(
                    currentStepCode,
                    currentStepLabel,
                    phaseForStep(currentStepCode),
                    STATUS_FAILED,
                    errorMessage,
                    null,
                    currentStepStartedAt(currentStepCode, now),
                    now,
                    consumesEvidenceForStep(currentStepCode),
                    producesEvidenceForStep(currentStepCode)
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
        replaceContextSections(contextSnapshot);
        steps.clear();
        steps.add(new AnalysisJobStepResponse(
                STEP_DETERMINISTIC_CONTEXT,
                STEP_DETERMINISTIC_CONTEXT_LABEL,
                PHASE_CONTEXT,
                STATUS_COMPLETED,
                "Backend zbudowal deterministic endpoint context bez uruchamiania AI.",
                contextItemCount(),
                createdAt,
                now,
                List.of(),
                List.of(FLOW_CONTEXT_EVIDENCE)
        ));
        result = new FlowExplorerResultResponse(
                STATUS_COMPLETED,
                request.systemId(),
                request.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                request.goal(),
                prompt,
                deterministicContextResponse(contextSnapshot),
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
                request.goal(),
                request.focusAreas(),
                request.resolvedSectionModes(),
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
                List.copyOf(contextSections),
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
        var response = (aiResponse != null
                ? aiResponse
                : FlowExplorerAiResponse.parseFallback("AI response was not available."))
                .withRequestContext(request.goal(), request.resolvedSectionModes());
        return new FlowExplorerResultResponse(
                STATUS_COMPLETED,
                request.systemId(),
                endpointId(),
                httpMethod(),
                endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                request.goal(),
                prompt,
                response,
                usage
        );
    }

    private FlowExplorerAiResponse deterministicContextResponse(FlowExplorerContextSnapshot contextSnapshot) {
        var confidence = contextSnapshot != null && contextSnapshot.coverage() != null
                ? contextSnapshot.coverage().confidence()
                : "low";
        var limitations = contextSnapshot != null ? contextSnapshot.limitations() : List.<String>of();
        var sections = FlowExplorerResultSectionModeResolver.activeOnly(request.resolvedSectionModes()).stream()
                .map(assignment -> new FlowExplorerResultSection(
                        assignment.id(),
                        assignment.title(),
                        assignment.mode(),
                        "",
                        List.of(),
                        List.of(),
                        List.of()
                ))
                .toList();
        return new FlowExplorerAiResponse(
                request.goal(),
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview(
                        "Deterministic context zostal zbudowany; AI nie zostalo jeszcze uruchomione.",
                        confidence,
                        List.of("flow-explorer/compact-flow-manifest.md")
                ),
                sections,
                limitations,
                List.of(),
                List.of(),
                confidence
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

    private void completeStep(String code, String label, String phase, String message, Integer itemCount) {
        completeStep(code, label, phase, message, itemCount, null);
    }

    private void completeStep(
            String code,
            String label,
            String phase,
            String message,
            Integer itemCount,
            AnalysisAiUsage usage
    ) {
        var now = Instant.now();
        replaceStep(new AnalysisJobStepResponse(
                code,
                label,
                phase,
                STATUS_COMPLETED,
                message,
                itemCount,
                currentStepStartedAt(code, createdAt),
                now,
                consumesEvidenceForStep(code),
                producesEvidenceForStep(code),
                usage
        ));
    }

    private Instant currentStepStartedAt(String code, Instant fallback) {
        return steps.stream()
                .filter(step -> code.equals(step.code()))
                .map(AnalysisJobStepResponse::startedAt)
                .findFirst()
                .orElse(fallback);
    }

    private void replaceStep(AnalysisJobStepResponse nextStep) {
        for (var index = 0; index < steps.size(); index++) {
            if (nextStep.code().equals(steps.get(index).code())) {
                steps.set(index, nextStep);
                return;
            }
        }
        steps.add(nextStep);
    }

    private String phaseForStep(String code) {
        return STEP_AI_ANALYSIS.equals(code) ? PHASE_AI : PHASE_CONTEXT;
    }

    private List<AnalysisEvidenceReference> consumesEvidenceForStep(String code) {
        return STEP_AI_ANALYSIS.equals(code) ? List.of(FLOW_CONTEXT_EVIDENCE) : List.of();
    }

    private List<AnalysisEvidenceReference> producesEvidenceForStep(String code) {
        return STEP_DETERMINISTIC_CONTEXT.equals(code) ? List.of(FLOW_CONTEXT_EVIDENCE) : List.of();
    }

    private void replaceContextSections(FlowExplorerContextSnapshot snapshot) {
        contextSections.clear();
        contextSections.addAll(contextSections(snapshot));
    }

    private int contextItemCount() {
        return contextSections.stream()
                .mapToInt(section -> section.items().size())
                .sum();
    }

    private static List<AnalysisEvidenceSection> contextSections(FlowExplorerContextSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }

        var items = new ArrayList<AnalysisEvidenceItem>();
        items.add(endpointItem(snapshot));
        items.add(coverageItem(snapshot));
        snapshot.repositories().stream()
                .map(FlowExplorerJobState::repositoryItem)
                .forEach(items::add);
        snapshot.flowNodes().stream()
                .map(FlowExplorerJobState::flowNodeItem)
                .forEach(items::add);
        snapshot.snippetCards().stream()
                .map(FlowExplorerJobState::snippetCardItem)
                .forEach(items::add);

        if (!snapshot.limitations().isEmpty()) {
            items.add(new AnalysisEvidenceItem(
                    "Visibility and parser limitations",
                    List.of(attribute("limitations", String.join("\n", snapshot.limitations())))
            ));
        }

        if (!snapshot.suggestedNextReads().isEmpty()) {
            items.add(new AnalysisEvidenceItem(
                    "Suggested next reads",
                    List.of(attribute("suggestedNextReads", String.join("\n", snapshot.suggestedNextReads())))
            ));
        }

        return List.of(new AnalysisEvidenceSection(
                FLOW_CONTEXT_EVIDENCE.provider(),
                FLOW_CONTEXT_EVIDENCE.category(),
                items
        ));
    }

    private static AnalysisEvidenceItem endpointItem(FlowExplorerContextSnapshot snapshot) {
        return new AnalysisEvidenceItem(
                "Endpoint target",
                List.of(
                        attribute("systemId", snapshot.systemId()),
                        attribute("systemName", snapshot.systemName()),
                        attribute("requestedBranch", snapshot.requestedBranch()),
                        attribute("resolvedRef", snapshot.resolvedRef()),
                        attribute("endpointId", snapshot.endpointId()),
                        attribute("httpMethod", snapshot.httpMethod()),
                        attribute("endpointPath", snapshot.endpointPath()),
                        attribute("endpointConfidence", snapshot.endpoint() != null ? snapshot.endpoint().confidence() : "")
                )
        );
    }

    private static AnalysisEvidenceItem coverageItem(FlowExplorerContextSnapshot snapshot) {
        var coverage = snapshot.coverage();
        if (coverage == null) {
            return new AnalysisEvidenceItem("Context coverage", List.of());
        }

        return new AnalysisEvidenceItem(
                "Context coverage",
                List.of(
                        attribute("endpointResolved", coverage.endpointResolved()),
                        attribute("repositoryRefCount", coverage.repositoryRefCount()),
                        attribute("attemptedRepositoryCount", coverage.attemptedRepositoryCount()),
                        attribute("flowNodeCount", coverage.flowNodeCount()),
                        attribute("methodCount", coverage.methodCount()),
                        attribute("relationCount", coverage.relationCount()),
                        attribute("snippetCardCount", coverage.snippetCardCount()),
                        attribute("snippetCharacterCount", coverage.snippetCharacterCount()),
                        attribute("snippetBudgetReached", coverage.snippetBudgetReached()),
                        attribute("unresolvedReferenceCount", coverage.unresolvedReferenceCount()),
                        attribute("limitationCount", coverage.limitationCount()),
                        attribute("maxDepthReached", coverage.maxDepthReached()),
                        attribute("maxFilesReached", coverage.maxFilesReached()),
                        attribute("readFileLimitReached", coverage.readFileLimitReached()),
                        attribute("confidence", coverage.confidence())
                )
        );
    }

    private static AnalysisEvidenceItem repositoryItem(FlowExplorerRepositoryContext repository) {
        return new AnalysisEvidenceItem(
                "Repository: " + fallback(repository.repositoryId(), repository.projectName()),
                List.of(
                        attribute("repositoryId", repository.repositoryId()),
                        attribute("projectName", repository.projectName()),
                        attribute("projectPath", repository.projectPath()),
                        attribute("resolvedRef", repository.resolvedRef()),
                        attribute("attempted", repository.attempted()),
                        attribute("selected", repository.selected()),
                        attribute("limitations", String.join("\n", repository.limitations()))
                )
        );
    }

    private static AnalysisEvidenceItem flowNodeItem(FlowExplorerFlowNode node) {
        return new AnalysisEvidenceItem(
                "Flow node: " + fallback(node.role(), node.filePath()),
                List.of(
                        attribute("role", node.role()),
                        attribute("filePath", node.filePath()),
                        attribute("methods", methods(node.methods())),
                        attribute("reason", node.reason()),
                        attribute("confidence", node.confidence()),
                        attribute("limitations", String.join("\n", node.limitations()))
                )
        );
    }

    private static AnalysisEvidenceItem snippetCardItem(FlowExplorerSnippetCard card) {
        return new AnalysisEvidenceItem(
                "Snippet card: " + fallback(card.role(), card.filePath()),
                List.of(
                        attribute("projectName", card.projectName()),
                        attribute("filePath", card.filePath()),
                        attribute("methods", methods(card.methods())),
                        attribute("requestedLines", lineRange(card.requestedStartLine(), card.requestedEndLine())),
                        attribute("returnedLines", lineRange(card.returnedStartLine(), card.returnedEndLine())),
                        attribute("totalLines", card.totalLines()),
                        attribute("truncated", card.truncated()),
                        attribute("characterCount", card.characterCount()),
                        attribute("reason", card.reason()),
                        attribute("limitations", String.join("\n", card.limitations()))
                )
        );
    }

    private static String methods(List<FlowExplorerFlowMethod> methods) {
        return methods.stream()
                .map(method -> "%s %s".formatted(method.methodName(), lineRange(method.lineStart(), method.lineEnd())))
                .toList()
                .toString();
    }

    private static String lineRange(int startLine, int endLine) {
        if (startLine <= 0 || endLine <= 0) {
            return "";
        }
        return "L%d-L%d".formatted(startLine, endLine);
    }

    private static AnalysisEvidenceAttribute attribute(String name, Object value) {
        return new AnalysisEvidenceAttribute(name, value != null ? String.valueOf(value) : "");
    }

    private static String fallback(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
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

        private AnalysisChatMessageResponse snapshot() {
            return new AnalysisChatMessageResponse(
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
