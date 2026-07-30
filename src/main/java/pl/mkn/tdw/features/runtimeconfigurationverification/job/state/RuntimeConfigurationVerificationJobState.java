package pl.mkn.tdw.features.runtimeconfigurationverification.job.state;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiRunResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiAssessment;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContextStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class RuntimeConfigurationVerificationJobState {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_COMPLETED_WITH_LIMITATIONS = "COMPLETED_WITH_LIMITATIONS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String STEP_SOURCE = "SOURCE";
    public static final String STEP_PARSE = "PARSE";
    public static final String STEP_DIFF = "DIFF";
    public static final String STEP_OPERATIONAL_CONTEXT = "OPERATIONAL_CONTEXT";
    public static final String STEP_CODE_GROUNDING = "CODE_GROUNDING";
    public static final String STEP_OWNERSHIP = "OWNERSHIP";
    public static final String STEP_AI = "AI";

    private final String jobId;
    private final RuntimeConfigurationVerificationJobStartRequest request;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String status;
    private String currentStepCode;
    private String currentStepLabel;
    private String errorCode;
    private String errorMessage;
    private String preparedPrompt;
    private RuntimeConfigurationDeterministicContext deterministic;
    private RuntimeConfigurationDeepContext deepContext;
    private RuntimeConfigurationVerificationResult result;
    private AnalysisReport report;
    private final List<AnalysisJobStepResponse> steps = new ArrayList<>();
    private final List<AnalysisEvidenceSection> contextSections = new ArrayList<>();
    private final List<AnalysisEvidenceSection> toolEvidenceSections = new ArrayList<>();
    private final List<AnalysisAiActivityEvent> aiActivityEvents = new ArrayList<>();

    public RuntimeConfigurationVerificationJobState(
            String jobId,
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        this.jobId = jobId;
        this.request = request;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = STATUS_QUEUED;
    }

    public synchronized void markSourceStarted() {
        start(STEP_SOURCE, "Configuration source", "CONFIGURATION");
    }

    public synchronized void markSourceCompleted() {
        complete(STEP_SOURCE, "Configuration snapshots loaded", null, null);
    }

    public synchronized void markParseStarted() {
        start(STEP_PARSE, "Parse and sanitize configuration", "CONFIGURATION");
    }

    public synchronized void markParseCompleted() {
        complete(STEP_PARSE, "Configuration parsed and sanitized", null, null);
    }

    public synchronized void markDiffStarted() {
        start(STEP_DIFF, "Deterministic comparison", "CONFIGURATION");
    }

    public synchronized void markDiffCompleted(RuntimeConfigurationDeterministicContext context) {
        deterministic = context;
        complete(
                STEP_DIFF,
                "Deterministic result ready",
                context != null ? context.differences().size() : 0,
                null
        );
        result = interimResult(context);
    }

    public synchronized void markOperationalContextStarted() {
        start(STEP_OPERATIONAL_CONTEXT, "Operational context", "DEEP");
    }

    public synchronized void markOperationalContextCompleted() {
        complete(STEP_OPERATIONAL_CONTEXT, "Operational context resolved", null, null);
    }

    public synchronized void markCodeGroundingStarted() {
        start(STEP_CODE_GROUNDING, "Code grounding", "DEEP");
    }

    public synchronized void markCodeGroundingCompleted() {
        complete(STEP_CODE_GROUNDING, "Focused code grounding completed", null, null);
    }

    public synchronized void markOwnershipStarted() {
        start(STEP_OWNERSHIP, "Ownership and handoff", "DEEP");
    }

    public synchronized void markOwnershipCompleted(RuntimeConfigurationDeepContext context) {
        deepContext = context;
        if (context != null && context.status() == RuntimeConfigurationDeepContextStatus.UNAVAILABLE) {
            skipIfAbsent(STEP_CODE_GROUNDING, "Code grounding", "DEEP",
                    "Skipped because DEEP context is unavailable");
            skipIfAbsent(STEP_OWNERSHIP, "Ownership and handoff", "DEEP",
                    "Ownership could not be resolved");
        } else {
            complete(
                    STEP_OWNERSHIP,
                    "Ownership and handoff resolved",
                    context != null && context.ownership() != null
                            ? context.ownership().primaryOwners().size()
                            + context.ownership().partnerOwners().size()
                            : 0,
                    null
            );
        }
        updatedAt = Instant.now();
    }

    public synchronized void markDeepFailed() {
        failCurrentDeepStep();
        skipIfAbsent(STEP_CODE_GROUNDING, "Code grounding", "DEEP",
                "Skipped because DEEP enrichment did not complete");
        skipIfAbsent(STEP_OWNERSHIP, "Ownership and handoff", "DEEP",
                "Skipped because DEEP enrichment did not complete");
        updatedAt = Instant.now();
    }

    public synchronized void markAiStarted(String prompt) {
        preparedPrompt = prompt;
        start(STEP_AI, "AI second opinion", "AI");
    }

    public synchronized void markAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        if (section == null) {
            return;
        }
        toolEvidenceSections.removeIf(existing ->
                java.util.Objects.equals(existing.provider(), section.provider())
                        && java.util.Objects.equals(existing.category(), section.category()));
        toolEvidenceSections.add(section);
        updatedAt = Instant.now();
    }

    public synchronized void markAiActivity(AnalysisAiActivityEvent event) {
        if (event != null) {
            aiActivityEvents.add(event);
            updatedAt = Instant.now();
        }
    }

    public synchronized void markCompleted(
            RuntimeConfigurationDeepContext deep,
            RuntimeConfigurationAiRunResult aiRun,
            String prompt
    ) {
        deepContext = deep;
        preparedPrompt = prompt;
        var assessment = aiRun != null ? aiRun.assessment() : null;
        var usage = aiRun != null ? aiRun.usage() : null;
        finishWithAssessment(assessment, usage, false);
    }

    public synchronized void markAiFailed(
            RuntimeConfigurationDeepContext deep,
            RuntimeConfigurationAiAssessment fallback,
            String prompt
    ) {
        deepContext = deep;
        preparedPrompt = prompt;
        finishWithAssessment(fallback, null, true);
    }

    public synchronized void markFailed(String code, String message) {
        var now = Instant.now();
        if (currentStepCode != null) {
            replaceStep(currentStepCode, step -> new AnalysisJobStepResponse(
                    step.code(), step.label(), step.phase(), "FAILED",
                    message, step.itemCount(), step.startedAt(), now,
                    step.consumesEvidence(), step.producesEvidence(), step.usage()
            ));
        }
        status = STATUS_FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = now;
        updatedAt = now;
        currentStepCode = null;
        currentStepLabel = null;
    }

    public synchronized RuntimeConfigurationVerificationJobStateSnapshot snapshot() {
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                jobId,
                request.mode(),
                request.repositoryId(),
                request.systemId(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                request.model(),
                request.reasoningEffort(),
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                List.copyOf(steps),
                List.copyOf(contextSections),
                List.copyOf(toolEvidenceSections),
                List.copyOf(aiActivityEvents),
                preparedPrompt,
                result,
                report,
                false
        );
    }

    private void finishWithAssessment(
            RuntimeConfigurationAiAssessment assessment,
            AnalysisAiUsage usage,
            boolean aiFailed
    ) {
        var now = Instant.now();
        var forcedIncomplete = aiFailed
                || assessment == null
                || assessment.combinedStatus() == RuntimeConfigurationVerificationStatus.INCOMPLETE
                || request.mode() == RuntimeConfigurationVerificationMode.DEEP
                && (deepContext == null || deepContext.status() != RuntimeConfigurationDeepContextStatus.COMPLETE);
        var finalStatus = forcedIncomplete
                ? RuntimeConfigurationVerificationStatus.INCOMPLETE
                : assessment.combinedStatus();
        var visibilityLimits = visibilityLimits(assessment, aiFailed);
        result = new RuntimeConfigurationVerificationResult(
                finalStatus,
                request.mode(),
                deterministic,
                assessment != null ? assessment.aiSecondOpinion() : null,
                assessment != null ? assessment.agreement() : null,
                deepContext,
                visibilityLimits,
                preparedPrompt,
                usage
        );
        report = assessment != null ? assessment.report() : null;
        replaceStep(STEP_AI, step -> new AnalysisJobStepResponse(
                step.code(), step.label(), step.phase(),
                aiFailed ? "FAILED" : "COMPLETED",
                aiFailed ? "AI second opinion did not complete; deterministic result is available"
                        : "AI second opinion completed",
                null, step.startedAt(), now,
                step.consumesEvidence(), step.producesEvidence(), usage
        ));
        status = forcedIncomplete ? STATUS_COMPLETED_WITH_LIMITATIONS : STATUS_COMPLETED;
        errorCode = aiFailed ? "RUNTIME_CONFIGURATION_AI_INCOMPLETE" : null;
        errorMessage = aiFailed
                ? "AI second opinion did not complete. Review the deterministic result."
                : null;
        currentStepCode = null;
        currentStepLabel = null;
        completedAt = now;
        updatedAt = now;
    }

    private RuntimeConfigurationVerificationResult interimResult(
            RuntimeConfigurationDeterministicContext context
    ) {
        return new RuntimeConfigurationVerificationResult(
                RuntimeConfigurationVerificationStatus.INCOMPLETE,
                request.mode(),
                context,
                null,
                null,
                null,
                List.of("AI second opinion has not completed yet."),
                null,
                null
        );
    }

    private List<String> visibilityLimits(
            RuntimeConfigurationAiAssessment assessment,
            boolean aiFailed
    ) {
        var limits = new LinkedHashSet<String>();
        if (deepContext != null) {
            limits.addAll(deepContext.visibilityLimits());
        }
        if (assessment != null && assessment.aiSecondOpinion() != null) {
            limits.addAll(assessment.aiSecondOpinion().visibilityLimits());
        }
        if (aiFailed) {
            limits.add("AI second opinion did not complete.");
        }
        return List.copyOf(limits);
    }

    private void start(String code, String label, String phase) {
        var now = Instant.now();
        status = STATUS_RUNNING;
        currentStepCode = code;
        currentStepLabel = label;
        updatedAt = now;
        var existing = findStep(code);
        if (existing == null) {
            steps.add(new AnalysisJobStepResponse(
                    code, label, phase, "RUNNING", null, null, now, null,
                    List.of(), List.of()
            ));
        }
    }

    private void complete(String code, String message, Integer itemCount, AnalysisAiUsage usage) {
        var now = Instant.now();
        var existing = findStep(code);
        if (existing == null) {
            steps.add(new AnalysisJobStepResponse(
                    code, label(code), phase(code), "COMPLETED", message, itemCount,
                    now, now, List.of(), List.of(), usage
            ));
        } else {
            replaceStep(code, step -> new AnalysisJobStepResponse(
                    step.code(), step.label(), step.phase(), "COMPLETED", message, itemCount,
                    step.startedAt(), now, step.consumesEvidence(), step.producesEvidence(), usage
            ));
        }
        updatedAt = now;
    }

    private void skipIfAbsent(String code, String label, String phase, String message) {
        if (findStep(code) == null) {
            var now = Instant.now();
            steps.add(new AnalysisJobStepResponse(
                    code, label, phase, "SKIPPED", message, 0,
                    now, now, List.of(), List.of()
            ));
        }
    }

    private void failCurrentDeepStep() {
        if (currentStepCode == null
                || STEP_AI.equals(currentStepCode)
                || STEP_SOURCE.equals(currentStepCode)
                || STEP_PARSE.equals(currentStepCode)
                || STEP_DIFF.equals(currentStepCode)) {
            return;
        }
        var now = Instant.now();
        replaceStep(currentStepCode, step -> new AnalysisJobStepResponse(
                step.code(), step.label(), step.phase(), "FAILED",
                "DEEP enrichment did not complete", step.itemCount(),
                step.startedAt(), now, step.consumesEvidence(), step.producesEvidence(), step.usage()
        ));
    }

    private AnalysisJobStepResponse findStep(String code) {
        return steps.stream().filter(step -> step.code().equals(code)).findFirst().orElse(null);
    }

    private void replaceStep(
            String code,
            java.util.function.Function<AnalysisJobStepResponse, AnalysisJobStepResponse> mapper
    ) {
        for (var index = 0; index < steps.size(); index++) {
            if (steps.get(index).code().equals(code)) {
                steps.set(index, mapper.apply(steps.get(index)));
                return;
            }
        }
    }

    private String label(String code) {
        return switch (code) {
            case STEP_SOURCE -> "Configuration source";
            case STEP_PARSE -> "Parse and sanitize configuration";
            case STEP_DIFF -> "Deterministic comparison";
            case STEP_OPERATIONAL_CONTEXT -> "Operational context";
            case STEP_CODE_GROUNDING -> "Code grounding";
            case STEP_OWNERSHIP -> "Ownership and handoff";
            case STEP_AI -> "AI second opinion";
            default -> code;
        };
    }

    private String phase(String code) {
        if (STEP_AI.equals(code)) {
            return "AI";
        }
        if (STEP_OPERATIONAL_CONTEXT.equals(code)
                || STEP_CODE_GROUNDING.equals(code)
                || STEP_OWNERSHIP.equals(code)) {
            return "DEEP";
        }
        return "CONFIGURATION";
    }
}
