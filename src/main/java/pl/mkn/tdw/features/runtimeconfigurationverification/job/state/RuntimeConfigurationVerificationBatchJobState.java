package pl.mkn.tdw.features.runtimeconfigurationverification.job.state;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationComponentRunSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.List;

public final class RuntimeConfigurationVerificationBatchJobState {

    private static final String STEP_COMPONENTS = "COMPONENTS";

    private final String jobId;
    private final RuntimeConfigurationVerificationJobStartRequest request;
    private final Instant createdAt;
    private final List<RuntimeConfigurationVerificationJobState> components;

    public RuntimeConfigurationVerificationBatchJobState(
            String jobId,
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        this.jobId = jobId;
        this.request = request;
        this.createdAt = Instant.now();
        this.components = java.util.stream.IntStream.range(0, request.systemIds().size())
                .mapToObj(index -> new RuntimeConfigurationVerificationJobState(
                        jobId + ":" + index,
                        request.forSystem(request.systemIds().get(index))
                ))
                .toList();
    }

    public List<RuntimeConfigurationVerificationJobState> components() {
        return components;
    }

    public synchronized RuntimeConfigurationVerificationJobStateSnapshot snapshot() {
        var componentSnapshots = components.stream()
                .map(RuntimeConfigurationVerificationJobState::snapshot)
                .toList();
        var completedCount = componentSnapshots.stream().filter(this::isTerminal).count();
        var resultCount = componentSnapshots.stream().filter(component -> component.result() != null).count();
        var anyRunning = componentSnapshots.stream().anyMatch(component ->
                RuntimeConfigurationVerificationJobState.STATUS_RUNNING.equals(component.status()));
        var allTerminal = completedCount == componentSnapshots.size();
        var status = aggregateStatus(componentSnapshots, completedCount, resultCount, anyRunning);
        var updatedAt = componentSnapshots.stream()
                .map(RuntimeConfigurationComponentRunSnapshot::updatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(createdAt);
        var completedAt = allTerminal
                ? componentSnapshots.stream()
                .map(RuntimeConfigurationComponentRunSnapshot::completedAt)
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
        return new RuntimeConfigurationVerificationJobStateSnapshot(
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
            List<RuntimeConfigurationComponentRunSnapshot> snapshots,
            long completedCount,
            long resultCount,
            boolean anyRunning
    ) {
        if (completedCount < snapshots.size()) {
            return anyRunning || completedCount > 0
                    ? RuntimeConfigurationVerificationJobState.STATUS_RUNNING
                    : RuntimeConfigurationVerificationJobState.STATUS_QUEUED;
        }
        if (resultCount == 0) {
            return RuntimeConfigurationVerificationJobState.STATUS_FAILED;
        }
        var limited = snapshots.stream().anyMatch(component ->
                !RuntimeConfigurationVerificationJobState.STATUS_COMPLETED.equals(component.status()));
        return limited
                ? RuntimeConfigurationVerificationJobState.STATUS_COMPLETED_WITH_LIMITATIONS
                : RuntimeConfigurationVerificationJobState.STATUS_COMPLETED;
    }

    private boolean isTerminal(RuntimeConfigurationComponentRunSnapshot component) {
        return RuntimeConfigurationVerificationJobState.STATUS_COMPLETED.equals(component.status())
                || RuntimeConfigurationVerificationJobState.STATUS_COMPLETED_WITH_LIMITATIONS.equals(component.status())
                || RuntimeConfigurationVerificationJobState.STATUS_FAILED.equals(component.status());
    }
}
