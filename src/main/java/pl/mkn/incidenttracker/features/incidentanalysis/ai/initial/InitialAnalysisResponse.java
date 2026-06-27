package pl.mkn.incidenttracker.features.incidentanalysis.ai.initial;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;

import java.util.List;

public record InitialAnalysisResponse(
        String providerName,
        String detectedProblem,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam,
        String functionalAnalysis,
        String technicalAnalysis,
        String confidence,
        List<String> visibilityLimits,
        String prompt,
        AnalysisAiUsage usage,
        String copilotSessionId
) {
    public InitialAnalysisResponse {
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public InitialAnalysisResponse(
            String providerName,
            String detectedProblem,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String functionalAnalysis,
            String technicalAnalysis,
            String confidence,
            List<String> visibilityLimits,
            String prompt,
            AnalysisAiUsage usage
    ) {
        this(
                providerName,
                detectedProblem,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                functionalAnalysis,
                technicalAnalysis,
                confidence,
                visibilityLimits,
                prompt,
                usage,
                null
        );
    }

    public InitialAnalysisResponse(
            String providerName,
            String detectedProblem,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String functionalAnalysis,
            String technicalAnalysis,
            String confidence,
            List<String> visibilityLimits,
            String prompt
    ) {
        this(
                providerName,
                detectedProblem,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                functionalAnalysis,
                technicalAnalysis,
                confidence,
                visibilityLimits,
                prompt,
                null,
                null
        );
    }
}

