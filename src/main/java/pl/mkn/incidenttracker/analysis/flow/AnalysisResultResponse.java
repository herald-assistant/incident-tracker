package pl.mkn.incidenttracker.analysis.flow;

public record AnalysisResultResponse(
        String status,
        String correlationId,
        String environment,
        String gitLabBranch,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String affectedFunction,
        String prompt
) {
}
