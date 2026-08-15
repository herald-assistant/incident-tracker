package pl.mkn.tdw.features.uiexplorer.job.state;

import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysis;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysisStatus;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobRequestSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiExplorerJobState {

    public static final String SCREEN_DISCOVERY_STEP = "SCREEN_DISCOVERY";
    public static final String SOURCE_CONTEXT_STEP = "SOURCE_CONTEXT";
    public static final String AI_PREPARATION_STEP = "AI_PREPARATION";
    public static final String AI_ANALYSIS_STEP = "AI_ANALYSIS";

    private static final String OUTPUT_AVAILABLE = "UI_EXPLORER_OUTPUT_AVAILABLE";
    private static final String ANALYSIS_IN_PROGRESS = "UI_EXPLORER_ANALYSIS_IN_PROGRESS";
    private static final String ANALYSIS_BLOCKED = "UI_EXPLORER_ANALYSIS_BLOCKED";

    private final String jobId;
    private final UiExplorerJobStartRequest request;
    private final Instant createdAt;
    private final Map<String, MutableStep> steps = new LinkedHashMap<>();
    private boolean executionClaimed;
    private UiExplorerJobRequestSnapshot requestSnapshot;
    private UiExplorerJobStatus status = UiExplorerJobStatus.QUEUED;
    private String currentStepCode = SCREEN_DISCOVERY_STEP;
    private String currentStepLabel = "Identify the selected screen";
    private String errorCode;
    private String errorMessage;
    private Instant updatedAt;
    private Instant completedAt;
    private List<AnalysisEvidenceSection> contextSections = List.of();
    private List<AnalysisEvidenceSection> toolEvidenceSections = List.of();
    private List<AnalysisAiActivityEvent> aiActivityEvents = List.of();
    private UiExplorerResultResponse result;
    private AnalysisReport report;
    private AnalysisAiUsage usage;
    private UiExplorerSourceRevision sourceRevision;

    public UiExplorerJobState(String jobId, UiExplorerJobStartRequest request) {
        this.jobId = jobId;
        this.request = request;
        createdAt = Instant.now();
        updatedAt = createdAt;
        sourceRevision = new UiExplorerSourceRevision(request.branch(), request.sourceRevision());
        requestSnapshot = requestSnapshot(request.systemId());
        steps.put(SCREEN_DISCOVERY_STEP, new MutableStep(
                SCREEN_DISCOVERY_STEP, "Identify the selected screen", "CONTEXT"));
        steps.put(SOURCE_CONTEXT_STEP, new MutableStep(
                SOURCE_CONTEXT_STEP, "Build bounded screen source context", "CONTEXT"));
        steps.put(AI_PREPARATION_STEP, new MutableStep(
                AI_PREPARATION_STEP, "Prepare bounded AI artifacts and prompt", "AI_PREPARATION"));
        steps.put(AI_ANALYSIS_STEP, new MutableStep(
                AI_ANALYSIS_STEP, "Analyze the selected screen", "AI"));
    }

    public synchronized boolean tryStartExecution() {
        if (executionClaimed) {
            return false;
        }
        executionClaimed = true;
        status = UiExplorerJobStatus.DISCOVERING_SCREEN;
        startStep(SCREEN_DISCOVERY_STEP, "The selected screen is being validated against the catalog source revision.");
        return true;
    }

    public synchronized void markSourceContextStarted() {
        status = UiExplorerJobStatus.BUILDING_CONTEXT;
        currentStepCode = SOURCE_CONTEXT_STEP;
        currentStepLabel = steps.get(SOURCE_CONTEXT_STEP).label;
        startStep(SOURCE_CONTEXT_STEP, "Bounded source context is being built for the selected screen.");
    }

    public synchronized void markSourceContextCompleted(
            UiExplorerSourceContextSnapshot context,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        var now = Instant.now();
        sourceRevision = context.sourceRevision();
        requestSnapshot = requestSnapshot(context.systemLabel());
        contextSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        completeStep(
                SCREEN_DISCOVERY_STEP,
                "COMPLETED",
                "The selected screen was validated against the catalog source revision.",
                1,
                null,
                now
        );
        var contextStatus = switch (context.status()) {
            case READY -> "COMPLETED";
            case PARTIAL -> "PARTIAL";
            case BLOCKED -> "BLOCKED";
        };
        completeStep(
                SOURCE_CONTEXT_STEP,
                contextStatus,
                context.status() == UiExplorerCoverageStatus.BLOCKED
                        ? "Deterministic source context is insufficient for the selected active sections."
                        : "Deterministic screen context, source manifest and active-section coverage were built.",
                context.sourceFiles().size(),
                null,
                now
        );
        currentStepCode = AI_PREPARATION_STEP;
        currentStepLabel = steps.get(AI_PREPARATION_STEP).label;
        updatedAt = now;
    }

    public synchronized void markAiPreparationStarted() {
        currentStepCode = AI_PREPARATION_STEP;
        currentStepLabel = steps.get(AI_PREPARATION_STEP).label;
        startStep(AI_PREPARATION_STEP, "Logical artifacts and the bounded AI prompt are being prepared.");
    }

    public synchronized void markAiPreparationCompleted(int artifactCount) {
        var now = Instant.now();
        completeStep(
                AI_PREPARATION_STEP,
                "COMPLETED",
                "Logical artifacts, trust boundaries and the canonical response contract were prepared.",
                artifactCount,
                null,
                now
        );
        currentStepCode = AI_ANALYSIS_STEP;
        currentStepLabel = steps.get(AI_ANALYSIS_STEP).label;
        updatedAt = now;
    }

    public synchronized void markAiAnalysisStarted() {
        status = UiExplorerJobStatus.ANALYZING;
        currentStepCode = AI_ANALYSIS_STEP;
        currentStepLabel = steps.get(AI_ANALYSIS_STEP).label;
        startStep(AI_ANALYSIS_STEP, "The selected screen is being analyzed from bounded source evidence.");
    }

    public synchronized void markAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) {
            return;
        }
        var updated = new ArrayList<>(toolEvidenceSections);
        updated.removeIf(current -> current.provider().equals(section.provider())
                && current.category().equals(section.category()));
        updated.add(section);
        toolEvidenceSections = List.copyOf(updated);
        updatedAt = Instant.now();
    }

    public synchronized void markAiActivity(AnalysisAiActivityEvent event) {
        if (event == null) {
            return;
        }
        if (event.eventId() != null && aiActivityEvents.stream()
                .anyMatch(current -> event.eventId().equals(current.eventId()))) {
            return;
        }
        var updated = new ArrayList<>(aiActivityEvents);
        updated.add(event);
        aiActivityEvents = List.copyOf(updated);
        updatedAt = Instant.now();
    }

    public synchronized void markAiAnalysisCompleted(UiExplorerAiAnalysis analysis) {
        var now = Instant.now();
        if (analysis.status() != UiExplorerAiAnalysisStatus.BLOCKED
                && (analysis.result() == null || analysis.report() == null)) {
            status = UiExplorerJobStatus.FAILED;
            errorCode = "UI_EXPLORER_AI_RESULT_UNAVAILABLE";
            errorMessage = "AI analysis finished without a publishable result and report.";
            completeStep(AI_ANALYSIS_STEP, "FAILED", errorMessage, 0, analysis.usage(), now);
            completeTerminal(now);
            return;
        }
        usage = analysis.usage();
        switch (analysis.status()) {
            case COMPLETED -> completeWithOutput(
                    UiExplorerJobStatus.COMPLETED,
                    "COMPLETED",
                    "Screen analysis completed.",
                    analysis,
                    now
            );
            case PARTIAL -> completeWithOutput(
                    UiExplorerJobStatus.PARTIAL,
                    "PARTIAL",
                    "Screen analysis completed with explicit visibility limitations.",
                    analysis,
                    now
            );
            case MALFORMED -> {
                errorCode = "UI_EXPLORER_AI_RESPONSE_MALFORMED";
                errorMessage = "AI returned an incomplete response; a safe partial result is available.";
                completeWithOutput(
                        UiExplorerJobStatus.PARTIAL,
                        "PARTIAL",
                        errorMessage,
                        analysis,
                        now
                );
            }
            case BLOCKED -> {
                status = UiExplorerJobStatus.BLOCKED;
                errorCode = "UI_EXPLORER_AI_READINESS_BLOCKED";
                errorMessage = analysis.limitations().isEmpty()
                        ? "UI Explorer AI readiness is blocked."
                        : analysis.limitations().get(0);
                result = null;
                report = null;
                completeStep(AI_ANALYSIS_STEP, "BLOCKED", errorMessage, 0, usage, now);
                completeTerminal(now);
            }
        }
    }

    public synchronized void markBlocked(String code, String message) {
        var now = Instant.now();
        status = UiExplorerJobStatus.BLOCKED;
        errorCode = code;
        errorMessage = message;
        failCurrentAndSkipRemaining("BLOCKED", message, now);
        completeTerminal(now);
    }

    public synchronized void markFailed(String code, String message) {
        var now = Instant.now();
        status = UiExplorerJobStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        failCurrentAndSkipRemaining("FAILED", message, now);
        completeTerminal(now);
    }

    public synchronized UiExplorerJobStateSnapshot snapshot() {
        var outputAvailability = outputAvailability();
        return new UiExplorerJobStateSnapshot(
                jobId,
                requestSnapshot,
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps.values().stream().map(MutableStep::snapshot).toList(),
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                List.of(),
                null,
                result,
                report,
                usage,
                sourceRevision,
                outputAvailability,
                outputAvailability.status() == UiExplorerOutputAvailabilityStatus.AVAILABLE
        );
    }

    private void completeWithOutput(
            UiExplorerJobStatus terminalStatus,
            String stepStatus,
            String message,
            UiExplorerAiAnalysis analysis,
            Instant now
    ) {
        status = terminalStatus;
        result = analysis.result();
        report = analysis.report();
        completeStep(AI_ANALYSIS_STEP, stepStatus, message, result != null ? 1 : 0, usage, now);
        completeTerminal(now);
    }

    private void completeTerminal(Instant now) {
        currentStepCode = null;
        currentStepLabel = null;
        completedAt = now;
        updatedAt = now;
    }

    private void failCurrentAndSkipRemaining(String failedStatus, String message, Instant now) {
        var reachedCurrent = false;
        for (var step : steps.values()) {
            if ("RUNNING".equals(step.status) && !step.code.equals(currentStepCode)) {
                completeStep(step.code, failedStatus, message, step.itemCount, step.usage, now);
            } else if (step.code.equals(currentStepCode)) {
                reachedCurrent = true;
                completeStep(step.code, failedStatus, message, step.itemCount, step.usage, now);
            } else if (reachedCurrent && "PENDING".equals(step.status)) {
                completeStep(step.code, "SKIPPED", "Skipped because an earlier step did not complete.", 0, null, now);
            }
        }
    }

    private void startStep(String code, String message) {
        var now = Instant.now();
        var step = steps.get(code);
        step.status = "RUNNING";
        step.message = message;
        step.startedAt = step.startedAt != null ? step.startedAt : now;
        step.completedAt = null;
        updatedAt = now;
    }

    private void completeStep(
            String code,
            String stepStatus,
            String message,
            Integer itemCount,
            AnalysisAiUsage stepUsage,
            Instant now
    ) {
        var step = steps.get(code);
        step.status = stepStatus;
        step.message = message;
        step.itemCount = itemCount;
        step.usage = stepUsage;
        step.startedAt = step.startedAt != null ? step.startedAt : createdAt;
        step.completedAt = now;
    }

    private UiExplorerJobRequestSnapshot requestSnapshot(String systemLabel) {
        return new UiExplorerJobRequestSnapshot(
                request.systemId(),
                systemLabel,
                request.branch(),
                request.screenId(),
                request.sourceRevision(),
                request.profile(),
                request.resolvedSectionModes(),
                request.scenarioDescription(),
                request.model(),
                request.reasoningEffort()
        );
    }

    private UiExplorerOutputAvailability outputAvailability() {
        if ((status == UiExplorerJobStatus.COMPLETED || status == UiExplorerJobStatus.PARTIAL)
                && result != null && report != null) {
            return new UiExplorerOutputAvailability(
                    UiExplorerOutputAvailabilityStatus.AVAILABLE,
                    OUTPUT_AVAILABLE,
                    status == UiExplorerJobStatus.COMPLETED
                            ? "UI Explorer result and report are available."
                            : "A partial UI Explorer result and report are available with explicit limitations.",
                    List.of()
            );
        }
        var terminal = status == UiExplorerJobStatus.BLOCKED || status == UiExplorerJobStatus.FAILED;
        return new UiExplorerOutputAvailability(
                UiExplorerOutputAvailabilityStatus.BLOCKED,
                terminal ? (errorCode != null ? errorCode : ANALYSIS_BLOCKED) : ANALYSIS_IN_PROGRESS,
                terminal
                        ? (errorMessage != null ? errorMessage : "UI Explorer analysis is blocked.")
                        : "UI Explorer analysis is still in progress.",
                steps.values().stream()
                        .filter(step -> !"COMPLETED".equals(step.status) && !"PARTIAL".equals(step.status))
                        .map(step -> step.code)
                        .toList()
        );
    }

    private static final class MutableStep {

        private final String code;
        private final String label;
        private final String phase;
        private String status = "PENDING";
        private String message = "Waiting for the previous step.";
        private Integer itemCount;
        private Instant startedAt;
        private Instant completedAt;
        private AnalysisAiUsage usage;

        private MutableStep(String code, String label, String phase) {
            this.code = code;
            this.label = label;
            this.phase = phase;
        }

        private AnalysisJobStepResponse snapshot() {
            return new AnalysisJobStepResponse(
                    code,
                    label,
                    phase,
                    status,
                    message,
                    itemCount,
                    startedAt,
                    completedAt,
                    List.of(),
                    List.of(),
                    usage
            );
        }
    }
}
