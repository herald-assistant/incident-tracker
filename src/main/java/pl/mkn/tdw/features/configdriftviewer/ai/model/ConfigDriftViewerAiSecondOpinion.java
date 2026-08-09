package pl.mkn.tdw.features.configdriftviewer.ai.model;

import java.util.List;

public record ConfigDriftViewerAiSecondOpinion(
        ConfigDriftViewerAiExecutionStatus executionStatus,
        ConfigDriftViewerAiConclusion conclusion,
        ConfigDriftViewerAiConfidence confidence,
        String summary,
        List<ConfigDriftViewerAiObservation> observations,
        List<String> recommendedHumanChecks,
        List<ConfigDriftViewerFunctionalImpact> functionalImpacts,
        List<String> visibilityLimits
) {

    public ConfigDriftViewerAiSecondOpinion {
        observations = observations != null ? List.copyOf(observations) : List.of();
        recommendedHumanChecks = recommendedHumanChecks != null ? List.copyOf(recommendedHumanChecks) : List.of();
        functionalImpacts = functionalImpacts != null ? List.copyOf(functionalImpacts) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public static ConfigDriftViewerAiSecondOpinion incomplete(String limitation) {
        return new ConfigDriftViewerAiSecondOpinion(
                ConfigDriftViewerAiExecutionStatus.INCOMPLETE,
                ConfigDriftViewerAiConclusion.INCONCLUSIVE,
                ConfigDriftViewerAiConfidence.LOW,
                "AI second opinion is unavailable.",
                List.of(),
                List.of("Zweryfikuj ręcznie wynik deterministyczny."),
                List.of(),
                limitation != null && !limitation.isBlank() ? List.of(limitation.trim()) : List.of()
        );
    }
}
