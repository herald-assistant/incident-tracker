package pl.mkn.tdw.features.configdriftviewer.job.api;

import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.List;

public record ConfigDriftViewerJobStateSnapshot(
        String jobId,
        ConfigDriftViewerMode mode,
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
        List<ConfigDriftViewerComponentRunSnapshot> components,
        boolean imported
) {

    public ConfigDriftViewerJobStateSnapshot {
        systemIds = systemIds != null ? List.copyOf(systemIds) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        components = components != null ? List.copyOf(components) : List.of();
    }

    public ConfigDriftViewerJobStateSnapshot asImported() {
        return new ConfigDriftViewerJobStateSnapshot(
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
