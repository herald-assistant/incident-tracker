package pl.mkn.incidenttracker.analysis;

public record AnalysisResultResponse(
        String status,
        String correlationId,
        String environment,
        String gitLabBranch,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String prompt
) {
}
