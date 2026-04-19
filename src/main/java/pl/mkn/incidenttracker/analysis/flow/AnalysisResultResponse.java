package pl.mkn.incidenttracker.analysis.flow;

public record AnalysisResultResponse(
        String status,
        String correlationId,
        String environment,
        String gitLabBranch,
        AnalysisResultVariants variants
) {
}
