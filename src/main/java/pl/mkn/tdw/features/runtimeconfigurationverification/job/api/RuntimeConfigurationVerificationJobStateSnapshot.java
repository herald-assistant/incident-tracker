package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.List;

public record RuntimeConfigurationVerificationJobStateSnapshot(
        String jobId,
        RuntimeConfigurationVerificationMode mode,
        String repositoryId,
        List<String> systemIds,
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
        List<RuntimeConfigurationComponentRunSnapshot> components,
        boolean imported
) {

    public RuntimeConfigurationVerificationJobStateSnapshot {
        systemIds = systemIds != null ? List.copyOf(systemIds) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        components = components != null ? List.copyOf(components) : List.of();
    }

    public RuntimeConfigurationVerificationJobStateSnapshot asImported() {
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                jobId,
                mode,
                repositoryId,
                systemIds,
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
                components,
                true
        );
    }

}
