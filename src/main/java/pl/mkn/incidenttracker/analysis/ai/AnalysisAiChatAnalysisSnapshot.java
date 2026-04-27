package pl.mkn.incidenttracker.analysis.ai;

public record AnalysisAiChatAnalysisSnapshot(
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String affectedFunction,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam
) {
}
