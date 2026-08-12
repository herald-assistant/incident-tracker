package pl.mkn.tdw.features.configdriftviewer.job.state;

import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerComponentRunSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.List;

public final class ConfigDriftViewerBatchJobState {

    private static final String STEP_COMPONENTS = "COMPONENTS";

    private final String jobId;
    private final ConfigDriftViewerJobStartRequest request;
    private final Instant createdAt;
    private final List<ConfigDriftViewerJobState> components;

    public ConfigDriftViewerBatchJobState(
            String jobId,
            ConfigDriftViewerJobStartRequest request
    ) {
        this.jobId = jobId;
        this.request = request;
        this.createdAt = Instant.now();
        this.components = java.util.stream.IntStream.range(0, request.systemIds().size())
                .mapToObj(index -> new ConfigDriftViewerJobState(
                        jobId + ":" + index,
                        request.forSystem(request.systemIds().get(index)),
                        createdAt
                ))
                .toList();
    }

    public List<ConfigDriftViewerJobState> components() {
        return components;
    }

    public synchronized ConfigDriftViewerJobStateSnapshot snapshot() {
        var componentSnapshots = components.stream()
                .map(ConfigDriftViewerJobState::snapshot)
                .toList();
        var completedCount = componentSnapshots.stream().filter(this::isTerminal).count();
        var resultCount = componentSnapshots.stream().filter(component -> component.result() != null).count();
        var anyRunning = componentSnapshots.stream().anyMatch(component ->
                ConfigDriftViewerJobState.STATUS_RUNNING.equals(component.status()));
        var allTerminal = completedCount == componentSnapshots.size();
        var status = aggregateStatus(componentSnapshots, completedCount, resultCount, anyRunning);
        var updatedAt = componentSnapshots.stream()
                .map(ConfigDriftViewerComponentRunSnapshot::updatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(createdAt);
        var completedAt = allTerminal
                ? componentSnapshots.stream()
                .map(ConfigDriftViewerComponentRunSnapshot::completedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(updatedAt)
                : null;
        var currentStepCode = allTerminal ? null : STEP_COMPONENTS;
        var currentStepLabel = allTerminal
                ? null
                : "Component comparisons (" + completedCount + "/" + componentSnapshots.size() + ")";
        var allFailed = allTerminal && resultCount == 0;
        var step = new AnalysisJobStepResponse(
                STEP_COMPONENTS,
                "Component comparisons",
                "CONFIGURATION",
                allTerminal ? (allFailed ? "FAILED" : "COMPLETED")
                        : (anyRunning || completedCount > 0 ? "RUNNING" : "QUEUED"),
                allTerminal ? completedCount + " component comparisons completed" : currentStepLabel,
                Math.toIntExact(completedCount),
                createdAt,
                completedAt,
                List.of(),
                List.of(),
                null
        );
        return new ConfigDriftViewerJobStateSnapshot(
                jobId,
                request.mode(),
                request.repositoryId(),
                request.systemIds(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                request.model(),
                request.reasoningEffort(),
                status,
                currentStepCode,
                currentStepLabel,
                allFailed ? "RUNTIME_CONFIGURATION_VERIFICATION_FAILED" : null,
                allFailed ? "No component comparison produced a result." : null,
                createdAt,
                updatedAt,
                completedAt,
                List.of(step),
                componentSnapshots,
                false
        );
    }

    private String aggregateStatus(
            List<ConfigDriftViewerComponentRunSnapshot> snapshots,
            long completedCount,
            long resultCount,
            boolean anyRunning
    ) {
        if (completedCount < snapshots.size()) {
            return anyRunning || completedCount > 0
                    ? ConfigDriftViewerJobState.STATUS_RUNNING
                    : ConfigDriftViewerJobState.STATUS_QUEUED;
        }
        if (resultCount == 0) {
            return ConfigDriftViewerJobState.STATUS_FAILED;
        }
        var limited = snapshots.stream().anyMatch(component ->
                !ConfigDriftViewerJobState.STATUS_COMPLETED.equals(component.status()));
        return limited
                ? ConfigDriftViewerJobState.STATUS_COMPLETED_WITH_LIMITATIONS
                : ConfigDriftViewerJobState.STATUS_COMPLETED;
    }

    private boolean isTerminal(ConfigDriftViewerComponentRunSnapshot component) {
        return ConfigDriftViewerJobState.STATUS_COMPLETED.equals(component.status())
                || ConfigDriftViewerJobState.STATUS_COMPLETED_WITH_LIMITATIONS.equals(component.status())
                || ConfigDriftViewerJobState.STATUS_FAILED.equals(component.status());
    }
}
