package pl.mkn.incidenttracker.analysis;

public record AnalysisFlowDiagramEdge(
        String id,
        String fromNodeId,
        String toNodeId,
        int sequence,
        String interactionType,
        String factStatus,
        String startedAt,
        Long durationMs,
        String supportSummary
) {
}
