package pl.mkn.incidenttracker.analysis.job;

import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisVariantStatus;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProviderDescriptor;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.flow.AnalysisResultResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AnalysisJobState {

    private static final String AI_CONSERVATIVE_STEP_CODE = "AI_ANALYSIS_CONSERVATIVE";
    private static final String AI_CONSERVATIVE_STEP_LABEL = "Budowanie analizy conservative AI";
    private static final String AI_EXPLORATORY_STEP_CODE = "AI_ANALYSIS_EXPLORATORY";
    private static final String AI_EXPLORATORY_STEP_LABEL = "Budowanie analizy exploratory AI";
    private static final AnalysisEvidenceProviderDescriptor AI_CONSERVATIVE_STEP_DESCRIPTOR =
            new AnalysisEvidenceProviderDescriptor(
                    AI_CONSERVATIVE_STEP_CODE,
                    AI_CONSERVATIVE_STEP_LABEL,
                    AnalysisStepPhase.AI,
                    List.of(),
                    List.of()
            );
    private static final AnalysisEvidenceProviderDescriptor AI_EXPLORATORY_STEP_DESCRIPTOR =
            new AnalysisEvidenceProviderDescriptor(
                    AI_EXPLORATORY_STEP_CODE,
                    AI_EXPLORATORY_STEP_LABEL,
                    AnalysisStepPhase.AI,
                    List.of(),
                    List.of()
            );

    private final String analysisId;
    private final String correlationId;
    private final Instant createdAt;
    private final List<StepState> steps;
    private final List<AnalysisEvidenceSection> evidenceSections;
    private final boolean exploratoryEnabled;

    private AnalysisJobStatus status;
    private String currentStepCode;
    private String currentStepLabel;
    private String environment;
    private String gitLabBranch;
    private String errorCode;
    private String errorMessage;
    private Instant updatedAt;
    private Instant completedAt;
    private AnalysisResultResponse result;

    AnalysisJobState(
            String analysisId,
            String correlationId,
            List<AnalysisEvidenceProviderDescriptor> providerDescriptors,
            boolean exploratoryEnabled,
            AnalysisEvidenceProviderDescriptor exploratoryProviderDescriptor
    ) {
        this.analysisId = analysisId;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = AnalysisJobStatus.QUEUED;
        this.steps = new ArrayList<>();
        this.evidenceSections = new ArrayList<>();
        this.exploratoryEnabled = exploratoryEnabled;

        for (var descriptor : providerDescriptors) {
            steps.add(new StepState(descriptor, null));
        }
        steps.add(new StepState(AI_CONSERVATIVE_STEP_DESCRIPTOR, AnalysisMode.CONSERVATIVE));
        if (exploratoryEnabled && exploratoryProviderDescriptor != null) {
            steps.add(new StepState(exploratoryProviderDescriptor, null));
            steps.add(new StepState(AI_EXPLORATORY_STEP_DESCRIPTOR, AnalysisMode.EXPLORATORY));
        }
    }

    synchronized void markEvidenceStepStarted(AnalysisEvidenceProviderDescriptor descriptor) {
        status = AnalysisJobStatus.COLLECTING_EVIDENCE;
        currentStepCode = descriptor.stepCode();
        currentStepLabel = descriptor.stepLabel();
        step(descriptor.stepCode()).markInProgress("Trwa pobieranie danych z tego kroku.");
        touch();
    }

    synchronized void markEvidenceStepCompleted(
            AnalysisEvidenceProviderDescriptor descriptor,
            AnalysisEvidenceSection section
    ) {
        if (section.hasItems()) {
            evidenceSections.add(section);
            updateRuntimeFacts();
        }

        var itemCount = section.items().size();
        step(descriptor.stepCode()).markCompleted(
                itemCount > 0
                        ? "Zebrano " + itemCount + " elementow danych."
                        : "Krok zakonczony bez nowych danych.",
                itemCount
        );
        touch();
    }

    synchronized void markEvidenceStepFailed(
            AnalysisEvidenceProviderDescriptor descriptor,
            String message
    ) {
        step(descriptor.stepCode()).markFailed(defaultMessage(message, "Krok evidence zakonczyl sie bledem."));
        touch();
    }

    synchronized void markAiStarted(AnalysisMode mode) {
        var step = step(aiStepCode(mode));
        status = AnalysisJobStatus.ANALYZING;
        currentStepCode = step.code;
        currentStepLabel = step.label;
        step.markInProgress(mode == AnalysisMode.EXPLORATORY
                ? "AI interpretuje exploratory flow i buduje wariant eksploracyjny."
                : "AI interpretuje bazowe evidence i buduje wariant conservative.");
        touch();
    }

    synchronized void markAiPromptPrepared(AnalysisMode mode, String preparedPrompt) {
        step(aiStepCode(mode)).preparedPrompt = normalizeBlank(preparedPrompt);
        touch();
    }

    synchronized void markAiCompleted(AnalysisMode mode) {
        step(aiStepCode(mode)).markCompleted("Analiza wariantu zakonczona.", null);
        touch();
    }

    synchronized void markAiFailed(AnalysisMode mode, String message) {
        step(aiStepCode(mode)).markFailed(defaultMessage(message, "Analiza wariantu zakonczyl sie bledem."));
        touch();
    }

    synchronized void markAiSkipped(AnalysisMode mode, String message) {
        if (!hasStep(aiStepCode(mode))) {
            return;
        }
        step(aiStepCode(mode)).markSkipped(message);
        touch();
    }

    synchronized void markCompleted(AnalysisResultResponse result) {
        this.result = result;
        this.environment = result.environment();
        this.gitLabBranch = result.gitLabBranch();
        this.status = AnalysisJobStatus.COMPLETED;
        this.currentStepCode = null;
        this.currentStepLabel = null;
        this.completedAt = Instant.now();

        ensureStepCompleted(AI_CONSERVATIVE_STEP_CODE, "Analiza conservative zakonczona.");

        if (exploratoryEnabled && result.variants() != null && result.variants().exploratory() != null) {
            var exploratoryVariant = result.variants().exploratory();
            if (exploratoryVariant.status() == AnalysisVariantStatus.SKIPPED) {
                if (hasStep("EXPLORATORY_FLOW_RECONSTRUCTION")
                        && step("EXPLORATORY_FLOW_RECONSTRUCTION").status == AnalysisJobStepStatus.PENDING) {
                    step("EXPLORATORY_FLOW_RECONSTRUCTION").markSkipped(
                            "Pominieto, bo nie udalo sie zbudowac dodatkowego flow."
                    );
                }
                markAiSkipped(AnalysisMode.EXPLORATORY, "Pominieto, bo nie bylo dodatkowego flow do interpretacji.");
            } else if (exploratoryVariant.status() == AnalysisVariantStatus.FAILED) {
                if (hasStep(AI_EXPLORATORY_STEP_CODE)
                        && step(AI_EXPLORATORY_STEP_CODE).status == AnalysisJobStepStatus.PENDING) {
                    step(AI_EXPLORATORY_STEP_CODE).markSkipped(
                            "Pominieto, bo exploratory flow zakonczyl sie bledem przed etapem AI."
                    );
                }
            } else if (exploratoryVariant.status() == AnalysisVariantStatus.COMPLETED) {
                ensureStepCompleted(AI_EXPLORATORY_STEP_CODE, "Analiza exploratory zakonczona.");
            }
        }

        skipRemainingPendingSteps("Pominieto po zakonczeniu analizy.");
        touch();
    }

    synchronized void markNotFound(String code, String message) {
        this.status = AnalysisJobStatus.NOT_FOUND;
        this.errorCode = code;
        this.errorMessage = message;
        this.currentStepCode = null;
        this.currentStepLabel = null;
        this.completedAt = Instant.now();
        skipRemainingPendingSteps("Pominieto, bo nie znaleziono danych diagnostycznych.");
        touch();
    }

    synchronized void markFailed(String code, String message) {
        this.status = AnalysisJobStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.completedAt = Instant.now();

        if (currentStepCode != null && hasStep(currentStepCode)) {
            step(currentStepCode).markFailed(defaultMessage(message, "Analiza zakonczyl sie bledem."));
        } else {
            ensureStepFailed(AI_CONSERVATIVE_STEP_CODE, defaultMessage(message, "Analiza zakonczyl sie bledem."));
        }

        currentStepCode = null;
        currentStepLabel = null;
        skipRemainingPendingSteps("Pominieto po bledzie analizy.");
        touch();
    }

    synchronized AnalysisJobResponse snapshot() {
        return new AnalysisJobResponse(
                analysisId,
                correlationId,
                status.name(),
                currentStepCode,
                currentStepLabel,
                environment,
                gitLabBranch,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps.stream().map(StepState::snapshot).toList(),
                List.copyOf(evidenceSections),
                result
        );
    }

    private void updateRuntimeFacts() {
        var deploymentContext = DeploymentContextEvidenceView.from(evidenceSections);

        if (environment == null || environment.isBlank()) {
            environment = deploymentContext.environment();
        }
        if (gitLabBranch == null || gitLabBranch.isBlank()) {
            gitLabBranch = deploymentContext.gitLabBranch();
        }
    }

    private StepState step(String code) {
        return steps.stream()
                .filter(step -> step.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown analysis step: " + code));
    }

    private boolean hasStep(String code) {
        return steps.stream().anyMatch(step -> step.code.equals(code));
    }

    private void ensureStepCompleted(String code, String message) {
        if (!hasStep(code)) {
            return;
        }
        var step = step(code);
        if (step.status == AnalysisJobStepStatus.PENDING || step.status == AnalysisJobStepStatus.IN_PROGRESS) {
            step.markCompleted(message, step.itemCount);
        }
    }

    private void ensureStepFailed(String code, String message) {
        if (!hasStep(code)) {
            return;
        }
        var step = step(code);
        if (step.status == AnalysisJobStepStatus.PENDING || step.status == AnalysisJobStepStatus.IN_PROGRESS) {
            step.markFailed(message);
        }
    }

    private void skipRemainingPendingSteps(String message) {
        for (var step : steps) {
            if (step.status == AnalysisJobStepStatus.PENDING) {
                step.markSkipped(message);
            }
        }
    }

    private String aiStepCode(AnalysisMode mode) {
        return mode == AnalysisMode.EXPLORATORY ? AI_EXPLORATORY_STEP_CODE : AI_CONSERVATIVE_STEP_CODE;
    }

    private String normalizeBlank(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private String defaultMessage(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static final class StepState {

        private final String code;
        private final String label;
        private final AnalysisStepPhase phase;
        private final List<AnalysisEvidenceReference> consumesEvidence;
        private final List<AnalysisEvidenceReference> producesEvidence;
        private final AnalysisMode variantMode;
        private AnalysisJobStepStatus status;
        private String message;
        private Integer itemCount;
        private Instant startedAt;
        private Instant completedAt;
        private String preparedPrompt;

        private StepState(AnalysisEvidenceProviderDescriptor descriptor, AnalysisMode variantMode) {
            this.code = descriptor.stepCode();
            this.label = descriptor.stepLabel();
            this.phase = descriptor.phase();
            this.consumesEvidence = List.copyOf(descriptor.consumesEvidence());
            this.producesEvidence = List.copyOf(descriptor.producesEvidence());
            this.variantMode = variantMode;
            this.status = AnalysisJobStepStatus.PENDING;
        }

        private void markInProgress(String message) {
            this.status = AnalysisJobStepStatus.IN_PROGRESS;
            this.message = message;
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            completedAt = null;
        }

        private void markCompleted(String message, Integer itemCount) {
            this.status = AnalysisJobStepStatus.COMPLETED;
            this.message = message;
            this.itemCount = itemCount;
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            completedAt = Instant.now();
        }

        private void markFailed(String message) {
            this.status = AnalysisJobStepStatus.FAILED;
            this.message = message;
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            completedAt = Instant.now();
        }

        private void markSkipped(String message) {
            this.status = AnalysisJobStepStatus.SKIPPED;
            this.message = message;
            if (completedAt == null) {
                completedAt = Instant.now();
            }
        }

        private AnalysisJobStepResponse snapshot() {
            return new AnalysisJobStepResponse(
                    code,
                    label,
                    phase.name(),
                    status.name(),
                    message,
                    itemCount,
                    startedAt,
                    completedAt,
                    variantMode != null ? variantMode.name() : null,
                    preparedPrompt,
                    consumesEvidence,
                    producesEvidence
            );
        }
    }

}
