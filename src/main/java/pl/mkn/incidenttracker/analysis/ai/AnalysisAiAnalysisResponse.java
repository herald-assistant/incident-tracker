package pl.mkn.incidenttracker.analysis.ai;

public record AnalysisAiAnalysisResponse(
        String providerName,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String prompt
) {
}
