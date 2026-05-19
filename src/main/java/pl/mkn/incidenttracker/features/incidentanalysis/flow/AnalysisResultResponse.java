package pl.mkn.incidenttracker.features.incidentanalysis.flow;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;

import java.util.List;

public record AnalysisResultResponse(
        String status,
        String correlationId,
        String environment,
        String gitLabBranch,
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
    public AnalysisResultResponse {
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public AnalysisResultResponse(
            String status,
            String correlationId,
            String environment,
            String gitLabBranch,
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
                status,
                correlationId,
                environment,
                gitLabBranch,
                detectedProblem,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                functionalAnalysis,
                technicalAnalysis,
                confidence,
                visibilityLimits,
                prompt,
                null
        );
    }
}
