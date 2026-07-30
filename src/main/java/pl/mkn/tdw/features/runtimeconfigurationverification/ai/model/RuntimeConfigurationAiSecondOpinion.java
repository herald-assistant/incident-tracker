package pl.mkn.tdw.features.runtimeconfigurationverification.ai.model;

import java.util.List;

public record RuntimeConfigurationAiSecondOpinion(
        RuntimeConfigurationAiExecutionStatus executionStatus,
        RuntimeConfigurationAiConclusion conclusion,
        RuntimeConfigurationAiConfidence confidence,
        String summary,
        List<RuntimeConfigurationAiObservation> observations,
        List<String> recommendedHumanChecks,
        List<RuntimeConfigurationFunctionalImpact> functionalImpacts,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationAiSecondOpinion {
        observations = observations != null ? List.copyOf(observations) : List.of();
        recommendedHumanChecks = recommendedHumanChecks != null ? List.copyOf(recommendedHumanChecks) : List.of();
        functionalImpacts = functionalImpacts != null ? List.copyOf(functionalImpacts) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public static RuntimeConfigurationAiSecondOpinion incomplete(String limitation) {
        return new RuntimeConfigurationAiSecondOpinion(
                RuntimeConfigurationAiExecutionStatus.INCOMPLETE,
                RuntimeConfigurationAiConclusion.INCONCLUSIVE,
                RuntimeConfigurationAiConfidence.LOW,
                "AI second opinion is unavailable.",
                List.of(),
                List.of("Zweryfikuj ręcznie wynik deterministyczny."),
                List.of(),
                limitation != null && !limitation.isBlank() ? List.of(limitation.trim()) : List.of()
        );
    }
}
