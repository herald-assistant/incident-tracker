package pl.mkn.incidenttracker.analysis.ai.initial;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;

public record InitialAnalysisResponse(
        String providerName,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String affectedFunction,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam,
        String prompt,
        AnalysisAiUsage usage
) {

    public InitialAnalysisResponse(
            String providerName,
            String summary,
            String detectedProblem,
            String recommendedAction,
            String rationale,
            String affectedFunction,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String prompt
    ) {
        this(
                providerName,
                summary,
                detectedProblem,
                recommendedAction,
                rationale,
                affectedFunction,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                prompt,
                null
        );
    }
}

