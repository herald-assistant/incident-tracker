package pl.mkn.tdw.features.operationalcontextassistance.job;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStatus;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalPreview;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalDecision;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceReviewDraft;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceSourceRevision;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OperationalContextAssistanceJobState {

    static final String COLLECT_CONTEXT = "COLLECT_CONTEXT";
    static final String PREPARE_AI = "PREPARE_AI";
    static final String ANALYZE = "ANALYZE";
    private static final Set<String> PUBLIC_EVENT_TYPES = Set.of(
            "assistant.turn_start", "assistant.turn_end", "assistant.message", "assistant.reasoning",
            "assistant.usage", "user.message", "session.start", "session.error", "session.usage_info",
            "tool.execution_start", "tool.execution_progress", "tool.execution_complete"
    );

    private final String jobId;
    private Instant createdAt = Instant.now();
    private final Map<String, MutableStep> steps = new LinkedHashMap<>();
    private OperationalContextAssistanceJobStatus status = OperationalContextAssistanceJobStatus.QUEUED;
    private String currentStepCode = COLLECT_CONTEXT;
    private String errorCode;
    private String errorMessage;
    private Instant updatedAt = createdAt;
    private Instant completedAt;
    private String catalogDigest;
    private String preparedPrompt;
    private OperationalContextAssistanceSourceRevision sourceRevision;
    private List<String> sourceRefs = List.of();
    private Set<String> requiredRepositoryScopeIds = Set.of();
    private final LinkedHashSet<String> visibilityLimits = new LinkedHashSet<>();
    private final List<AnalysisAiActivityEvent> aiActivityEvents = new ArrayList<>();
    private AnalysisAiUsage usage;
    private OperationalContextAssistanceDraft draft;
    private List<OperationalContextAssistanceProposalPreview> previews = List.of();
    private final List<OperationalContextAssistanceProposalDecision> proposalDecisions = new ArrayList<>();
    private OperationalContextAssistanceReviewDraft reviewDraft;
    private boolean executionClaimed;

    OperationalContextAssistanceJobState(String jobId) {
        this.jobId = jobId;
        steps.put(COLLECT_CONTEXT, new MutableStep(COLLECT_CONTEXT, "Zbierz kontekst", "CONTEXT"));
        steps.put(PREPARE_AI, new MutableStep(PREPARE_AI, "Przygotuj asystę AI", "AI_PREPARATION"));
        steps.put(ANALYZE, new MutableStep(ANALYZE, "Przygotuj propozycje", "AI"));
    }

    static OperationalContextAssistanceJobState restore(
            OperationalContextAssistanceJobSnapshot snapshot, Set<String> requiredRepositoryScopeIds
    ) {
        var state = new OperationalContextAssistanceJobState(snapshot.jobId());
        state.createdAt = snapshot.createdAt();
        state.updatedAt = snapshot.updatedAt();
        state.completedAt = snapshot.completedAt();
        state.status = snapshot.status();
        state.currentStepCode = snapshot.currentStepCode();
        state.errorCode = snapshot.errorCode();
        state.errorMessage = snapshot.errorMessage();
        state.catalogDigest = snapshot.catalogDigest();
        state.preparedPrompt = snapshot.preparedPrompt();
        state.sourceRevision = snapshot.sourceRevision();
        state.sourceRefs = snapshot.sourceRefs();
        state.requiredRepositoryScopeIds = Set.copyOf(requiredRepositoryScopeIds);
        state.visibilityLimits.addAll(snapshot.visibilityLimits());
        state.aiActivityEvents.addAll(snapshot.aiActivityEvents());
        state.usage = snapshot.usage();
        state.draft = snapshot.draft();
        state.previews = snapshot.previews();
        state.proposalDecisions.addAll(snapshot.proposalDecisions());
        state.reviewDraft = snapshot.reviewDraft();
        for (var step : snapshot.steps()) {
            state.steps.put(step.code(), MutableStep.restore(step));
        }
        state.executionClaimed = true;
        return state;
    }

    synchronized boolean start() {
        if (executionClaimed) {
            return false;
        }
        executionClaimed = true;
        status = OperationalContextAssistanceJobStatus.COLLECTING_CONTEXT;
        steps.get(COLLECT_CONTEXT).start();
        updatedAt = Instant.now();
        return true;
    }

    synchronized void contextCollected(
            String digest,
            OperationalContextAssistanceSourceRevision revision,
            List<String> limits,
            int fileCount
    ) {
        catalogDigest = digest;
        sourceRevision = revision;
        visibilityLimits.addAll(limits);
        steps.get(COLLECT_CONTEXT).complete("COMPLETED", "Zebrano aktualny katalog i wybrane źródło.", fileCount, null);
        status = OperationalContextAssistanceJobStatus.AI_PREPARATION;
        currentStepCode = PREPARE_AI;
        steps.get(PREPARE_AI).start();
        updatedAt = Instant.now();
    }

    synchronized void prepared(String prompt, List<String> refs, List<String> limits) {
        preparedPrompt = prompt;
        sourceRefs = refs != null ? List.copyOf(refs) : List.of();
        visibilityLimits.addAll(limits);
        steps.get(PREPARE_AI).complete("COMPLETED", "Przygotowano pełny katalog i reguły dla AI.", sourceRefs.size(), null);
        status = OperationalContextAssistanceJobStatus.ANALYZING;
        currentStepCode = ANALYZE;
        steps.get(ANALYZE).start();
        updatedAt = Instant.now();
    }

    synchronized void activity(AnalysisAiActivityEvent event) {
        if (event == null || aiActivityEvents.size() >= 500) {
            return;
        }
        if (event.eventId() != null && aiActivityEvents.stream()
                .anyMatch(current -> event.eventId().equals(current.eventId()))) {
            return;
        }
        aiActivityEvents.add(publicActivity(event));
        updatedAt = Instant.now();
    }

    private AnalysisAiActivityEvent publicActivity(AnalysisAiActivityEvent event) {
        // SDK details can contain prompt excerpts, reasoning and tool payloads. Publish
        // only the event kind, action, safe tool identity and numeric usage metadata.
        var category = switch (event.category() != null ? event.category() : "") {
            case "SESSION", "TURN", "MESSAGE", "TOOL", "USAGE", "RUNTIME", "CONTEXT", "ERROR" -> event.category();
            default -> "OTHER";
        };
        var status = switch (event.status() != null ? event.status() : "") {
            case "STARTED", "PROGRESS", "COMPLETED", "FAILED", "INFO", "BLOCKED" -> event.status();
            default -> "INFO";
        };
        var toolName = safeToolName(event.toolName());
        var title = switch (category) {
            case "SESSION" -> "Sesja AI";
            case "TURN" -> "Turn AI";
            case "MESSAGE" -> "Wiadomość AI";
            case "TOOL" -> toolName != null ? toolName : "Narzędzie Copilota";
            case "USAGE" -> "Wykorzystanie AI";
            case "RUNTIME" -> "Środowisko AI";
            default -> "Aktywność AI";
        };
        return new AnalysisAiActivityEvent(
                event.eventId(), event.parentEventId(),
                event.type() != null && PUBLIC_EVENT_TYPES.contains(event.type())
                        ? event.type() : "assistance.activity",
                category, status,
                title, safeActivitySummary(event, category, toolName), event.turnId(), event.interactionId(),
                event.toolCallId(), toolName, event.timestamp(), safeActivityDetails(event, category)
        );
    }

    private String safeToolName(String value) {
        return value != null && value.matches(
                "(?:gitlab_(?:list_repository_branches|list_repository_tree|list_repository_files|search_repository_files|read_repository_file)|skill)")
                ? value : null;
    }

    private String safeActivitySummary(AnalysisAiActivityEvent event, String category, String toolName) {
        return switch (event.type() != null ? event.type() : "") {
            case "user.message" -> "Aplikacja wysłała przygotowany prompt do Copilota.";
            case "assistant.message" -> assistantMessageSummary(event.details());
            case "assistant.reasoning" -> "Copilot planuje kolejny krok analizy.";
            case "tool.execution_start" -> toolName != null
                    ? "Copilot uruchomił " + toolName + "." : "Copilot uruchomił narzędzie.";
            case "tool.execution_progress" -> "Narzędzie jest wykonywane.";
            case "tool.execution_complete" -> "FAILED".equals(event.status())
                    ? "Narzędzie zakończyło się błędem." : "Narzędzie zakończyło działanie.";
            case "assistant.usage" -> usageSummary(event.details());
            case "session.error" -> "Sesja Copilota zgłosiła błąd.";
            default -> switch (category) {
                case "TURN" -> "Copilot przetwarza kolejny etap.";
                case "SESSION" -> "Zmieniono stan sesji Copilota.";
                case "RUNTIME" -> "Sprawdzono środowisko Copilota.";
                case "CONTEXT" -> "Zmieniono kontekst sesji Copilota.";
                default -> "Zarejestrowano etap pracy Copilota.";
            };
        };
    }

    private String assistantMessageSummary(Map<String, Object> details) {
        var toolRequests = safeCount(details.get("toolRequestCount"));
        if (toolRequests > 0) {
            var noun = toolRequests == 1 ? "wywołanie"
                    : toolRequests % 10 >= 2 && toolRequests % 10 <= 4
                    && (toolRequests % 100 < 12 || toolRequests % 100 > 14) ? "wywołania" : "wywołań";
            return "Copilot zaplanował " + toolRequests + " " + noun + " narzędzi.";
        }
        var contentLength = safeCount(details.get("contentLength"));
        return contentLength > 0
                ? "Copilot przygotował odpowiedź (" + contentLength + " znaków)."
                : "Copilot przygotował kolejny krok analizy.";
    }

    private String usageSummary(Map<String, Object> details) {
        var model = safeModel(details.get("model"));
        return "Model " + (model != null ? model : "AI")
                + ": input " + safeCount(details.get("inputTokens"))
                + ", output " + safeCount(details.get("outputTokens")) + " tokenów.";
    }

    private long safeCount(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return 0;
        }
        return Math.max(0, number.longValue());
    }

    private String safeModel(Object value) {
        return value instanceof String model && model.matches("[A-Za-z0-9._ -]{1,80}") ? model : null;
    }

    private Map<String, Object> safeActivityDetails(AnalysisAiActivityEvent event, String category) {
        if (!"USAGE".equals(category) && !"MESSAGE".equals(category)) {
            return Map.of();
        }
        var safe = new LinkedHashMap<String, Object>();
        var numericKeys = "USAGE".equals(category)
                ? List.of("inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "cost", "durationMs")
                : List.of("toolRequestCount", "contentLength", "reasoningTextLength");
        for (var key : numericKeys) {
            var value = event.details().get(key);
            if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0) {
                safe.put(key, value);
            }
        }
        if ("USAGE".equals(category)) {
            var model = safeModel(event.details().get("model"));
            if (model != null) {
                safe.put("model", model);
            }
        }
        return Map.copyOf(safe);
    }

    synchronized void usage(AnalysisAiUsage value) {
        usage = value;
        updatedAt = Instant.now();
    }

    synchronized void sourceRefs(List<String> refs) {
        sourceRefs = refs != null ? List.copyOf(refs) : List.of();
        updatedAt = Instant.now();
    }

    synchronized void requiredRepositoryScopeIds(Set<String> scopeIds) {
        requiredRepositoryScopeIds = scopeIds == null ? Set.of() : Set.copyOf(scopeIds);
    }

    synchronized Set<String> requiredRepositoryScopeIds() {
        return requiredRepositoryScopeIds;
    }

    synchronized void complete(
            OperationalContextAssistanceDraft draft,
            List<OperationalContextAssistanceProposalPreview> previews,
            AnalysisAiUsage usage,
            List<String> limits,
            boolean partial
    ) {
        this.draft = draft;
        this.previews = previews != null ? List.copyOf(previews) : List.of();
        this.usage = usage;
        visibilityLimits.addAll(limits);
        visibilityLimits.addAll(draft.visibilityLimits());
        status = partial ? OperationalContextAssistanceJobStatus.PARTIAL
                : OperationalContextAssistanceJobStatus.COMPLETED;
        steps.get(ANALYZE).complete(partial ? "PARTIAL" : "COMPLETED",
                "Propozycje AI są gotowe do przeglądu.", draft.proposals().size(), usage);
        completeTerminal();
    }

    synchronized void blocked(String code, String message) {
        terminal(OperationalContextAssistanceJobStatus.BLOCKED, code, message);
    }

    synchronized void blockedWithDraft(
            String code, String message, OperationalContextAssistanceDraft draft, List<String> limits
    ) {
        this.draft = draft;
        visibilityLimits.addAll(limits);
        visibilityLimits.addAll(draft.visibilityLimits());
        terminal(OperationalContextAssistanceJobStatus.BLOCKED, code, message);
    }

    synchronized void failed(String code, String message) {
        terminal(OperationalContextAssistanceJobStatus.FAILED, code, message);
    }

    private void terminal(OperationalContextAssistanceJobStatus terminalStatus, String code, String message) {
        status = terminalStatus;
        errorCode = code;
        errorMessage = message;
        var step = steps.get(currentStepCode);
        if (step.startedAt != null && step.completedAt == null) {
            step.complete(terminalStatus.name(), message, null, usage);
        }
        completeTerminal();
    }

    private void completeTerminal() {
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    synchronized void recordDecision(OperationalContextAssistanceProposalDecision decision) {
        if (decision.proposalIndex() != proposalDecisions.size()) {
            throw new IllegalStateException("Assistance proposal decisions must stay ordered");
        }
        proposalDecisions.add(decision);
        updatedAt = Instant.now();
    }

    synchronized void saveReview(OperationalContextAssistanceReviewDraft value) {
        reviewDraft = value;
        updatedAt = Instant.now();
    }

    synchronized OperationalContextAssistanceJobSnapshot snapshot() {
        return new OperationalContextAssistanceJobSnapshot(
                jobId, status, currentStepCode, steps.get(currentStepCode).label,
                errorCode, errorMessage, createdAt, updatedAt, completedAt,
                steps.values().stream().map(MutableStep::snapshot).toList(),
                List.copyOf(aiActivityEvents), usage, catalogDigest, sourceRevision,
                sourceRefs, List.copyOf(visibilityLimits), draft, previews,
                List.copyOf(proposalDecisions), preparedPrompt, reviewDraft
        );
    }

    private static final class MutableStep {
        private final String code;
        private final String label;
        private final String phase;
        private String status = "PENDING";
        private String message;
        private Integer itemCount;
        private Instant startedAt;
        private Instant completedAt;
        private AnalysisAiUsage usage;

        private MutableStep(String code, String label, String phase) {
            this.code = code;
            this.label = label;
            this.phase = phase;
        }

        private static MutableStep restore(AnalysisJobStepResponse snapshot) {
            var step = new MutableStep(snapshot.code(), snapshot.label(), snapshot.phase());
            step.status = snapshot.status();
            step.message = snapshot.message();
            step.itemCount = snapshot.itemCount();
            step.startedAt = snapshot.startedAt();
            step.completedAt = snapshot.completedAt();
            step.usage = snapshot.usage();
            return step;
        }

        private void start() {
            status = "IN_PROGRESS";
            startedAt = Instant.now();
        }

        private void complete(String resultStatus, String resultMessage, Integer resultCount, AnalysisAiUsage resultUsage) {
            status = resultStatus;
            message = resultMessage;
            itemCount = resultCount;
            usage = resultUsage;
            completedAt = Instant.now();
        }

        private AnalysisJobStepResponse snapshot() {
            return new AnalysisJobStepResponse(
                    code, label, phase, status, message, itemCount, startedAt, completedAt,
                    List.of(), List.of(), usage
            );
        }
    }
}
