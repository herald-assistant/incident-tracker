package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import java.time.Instant;

public record FlowExplorerJobStepResponse(
        String code,
        String label,
        String status,
        String message,
        Instant startedAt,
        Instant completedAt
) {
}

