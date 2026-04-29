package pl.mkn.incidenttracker.analysis.ai.analysis;

import pl.mkn.incidenttracker.analysis.ai.usage.AnalysisAiUsage;

public record AnalysisAiAnalysisResponse(
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

    public AnalysisAiAnalysisResponse(
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

