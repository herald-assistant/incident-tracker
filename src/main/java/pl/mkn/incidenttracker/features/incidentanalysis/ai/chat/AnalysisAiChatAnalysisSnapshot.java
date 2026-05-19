package pl.mkn.incidenttracker.features.incidentanalysis.ai.chat;

import java.util.List;

public record AnalysisAiChatAnalysisSnapshot(
        String detectedProblem,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam,
        String functionalAnalysis,
        String technicalAnalysis,
        String confidence,
        List<String> visibilityLimits
) {
    public AnalysisAiChatAnalysisSnapshot {
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}

