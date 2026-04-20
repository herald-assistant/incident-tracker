package pl.mkn.incidenttracker.analysis.job;

import org.springframework.util.StringUtils;
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

    private static final String AI_STEP_CODE = "AI_ANALYSIS";
    private static final String AI_STEP_LABEL = "Budowanie koncowej analizy AI";
    private static final AnalysisEvidenceProviderDescriptor AI_STEP_DESCRIPTOR =
            new AnalysisEvidenceProviderDescriptor(
                    AI_STEP_CODE,
                    AI_STEP_LABEL,
                    AnalysisStepPhase.AI,
                    List.of(),
                    List.of()
            );

    private final String analysisId;
    private final String correlationId;
    private final Instant createdAt;
    private final List<StepState> steps;
    private final List<AnalysisEvidenceSection> evidenceSections;
    private final List<AnalysisEvidenceSection> toolEvidenceSections;

    private AnalysisJobStatus status;
    private String currentStepCode;
    private String currentStepLabel;
    private String environment;
    private String gitLabBranch;
    private String errorCode;
    private String errorMessage;
    private String preparedPrompt;
    private Instant updatedAt;
    private Instant completedAt;
    private AnalysisResultResponse result;

    AnalysisJobState(String analysisId, String correlationId, List<AnalysisEvidenceProviderDescriptor> providerDescriptors) {
        this.analysisId = analysisId;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = AnalysisJobStatus.QUEUED;
        this.steps = new ArrayList<>();
        this.evidenceSections = new ArrayList<>();
        this.toolEvidenceSections = new ArrayList<>();

        for (var descriptor : providerDescriptors) {
            steps.add(new StepState(descriptor));
        }
        steps.add(new StepState(AI_STEP_DESCRIPTOR));
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

    synchronized void markAiStarted() {
        status = AnalysisJobStatus.ANALYZING;
        currentStepCode = AI_STEP_CODE;
        currentStepLabel = AI_STEP_LABEL;
        step(AI_STEP_CODE).markInProgress("AI interpretuje zebrane dane i buduje diagnoze.");
        touch();
    }

    synchronized void markAiPromptPrepared(String preparedPrompt) {
        this.preparedPrompt = StringUtils.hasText(preparedPrompt) ? preparedPrompt : null;
        touch();
    }

    synchronized void markAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) {
            return;
        }

        upsertSection(toolEvidenceSections, section);
        touch();
    }

    synchronized void markAiSkipped(String message) {
        step(AI_STEP_CODE).markSkipped(message);
        currentStepCode = null;
        currentStepLabel = null;
        touch();
    }

    synchronized void markCompleted(AnalysisResultResponse result) {
        this.result = result;
        this.environment = result.environment();
        this.gitLabBranch = result.gitLabBranch();
        if (!StringUtils.hasText(preparedPrompt) && StringUtils.hasText(result.prompt())) {
            this.preparedPrompt = result.prompt();
        }
        this.status = AnalysisJobStatus.COMPLETED;
        this.currentStepCode = null;
        this.currentStepLabel = null;
        this.completedAt = Instant.now();
        step(AI_STEP_CODE).markCompleted("Analiza zakonczona.", null);
        touch();
    }

    synchronized void markNotFound(String code, String message) {
        this.status = AnalysisJobStatus.NOT_FOUND;
        this.errorCode = code;
        this.errorMessage = message;
        this.currentStepCode = null;
        this.currentStepLabel = null;
        this.completedAt = Instant.now();
        step(AI_STEP_CODE).markSkipped("Pominieto, bo nie znaleziono danych diagnostycznych.");
        touch();
    }

    synchronized void markFailed(String code, String message) {
        this.status = AnalysisJobStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.completedAt = Instant.now();

        if (currentStepCode != null) {
            step(currentStepCode).markFailed(message);
        } else {
            step(AI_STEP_CODE).markFailed(message);
        }

        currentStepCode = null;
        currentStepLabel = null;
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
                List.copyOf(toolEvidenceSections),
                preparedPrompt,
                result
        );
    }

    private void upsertSection(List<AnalysisEvidenceSection> sections, AnalysisEvidenceSection candidate) {
        for (int index = 0; index < sections.size(); index++) {
            var current = sections.get(index);
            if (current.provider().equals(candidate.provider()) && current.category().equals(candidate.category())) {
                sections.set(index, candidate);
                return;
            }
        }

        sections.add(candidate);
    }

    private void updateRuntimeFacts() {
        var deploymentContext = DeploymentContextEvidenceView.from(evidenceSections);

        if (!StringUtils.hasText(environment)) {
            environment = deploymentContext.environment();
        }
        if (!StringUtils.hasText(gitLabBranch)) {
            gitLabBranch = deploymentContext.gitLabBranch();
        }
    }

    private StepState step(String code) {
        return steps.stream()
                .filter(step -> step.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown analysis step: " + code));
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
        private AnalysisJobStepStatus status;
        private String message;
        private Integer itemCount;
        private Instant startedAt;
        private Instant completedAt;

        private StepState(AnalysisEvidenceProviderDescriptor descriptor) {
            this.code = descriptor.stepCode();
            this.label = descriptor.stepLabel();
            this.phase = descriptor.phase();
            this.consumesEvidence = List.copyOf(descriptor.consumesEvidence());
            this.producesEvidence = List.copyOf(descriptor.producesEvidence());
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
            completedAt = Instant.now();
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
                    consumesEvidence,
                    producesEvidence
            );
        }
    }

}
