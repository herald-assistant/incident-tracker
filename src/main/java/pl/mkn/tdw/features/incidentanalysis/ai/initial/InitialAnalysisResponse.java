package pl.mkn.tdw.features.incidentanalysis.ai.initial;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

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
        String copilotSessionId,
        AnalysisReport report
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
            AnalysisAiUsage usage,
            String copilotSessionId
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
                copilotSessionId,
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
                null,
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
                null,
                null
        );
    }
}

