package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record RuntimeConfigurationVerificationJobStateSnapshot(
        String jobId,
        RuntimeConfigurationVerificationMode mode,
        String repositoryId,
        String systemId,
        String sourceBranch,
        String targetBranch,
        String codeRef,
        String aiModel,
        String reasoningEffort,
        String status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        String preparedPrompt,
        RuntimeConfigurationVerificationResult result,
        AnalysisReport report,
        boolean imported
) {

    public RuntimeConfigurationVerificationJobStateSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
    }

    public RuntimeConfigurationVerificationJobStateSnapshot(
            String jobId,
            RuntimeConfigurationVerificationMode mode,
            String repositoryId,
            String systemId,
            String sourceBranch,
            String targetBranch,
            String codeRef,
            String aiModel,
            String reasoningEffort,
            String status,
            String currentStepCode,
            String currentStepLabel,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            List<AnalysisJobStepResponse> steps,
            List<AnalysisEvidenceSection> contextSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            List<AnalysisAiActivityEvent> aiActivityEvents,
            String preparedPrompt
    ) {
        this(
                jobId,
                mode,
                repositoryId,
                systemId,
                sourceBranch,
                targetBranch,
                codeRef,
                aiModel,
                reasoningEffort,
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                preparedPrompt,
                null,
                null,
                false
        );
    }

    public RuntimeConfigurationVerificationJobStateSnapshot asImported() {
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                jobId,
                mode,
                repositoryId,
                systemId,
                sourceBranch,
                targetBranch,
                codeRef,
                aiModel,
                reasoningEffort,
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                preparedPrompt,
                result,
                report,
                true
        );
    }
}
