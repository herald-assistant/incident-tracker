package pl.mkn.incidenttracker.analysis.ai;

import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;

public record AnalysisAiAnalysisResponse(
        String providerName,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        AnalysisProblemNature problemNature,
        AnalysisConfidence confidence,
        String prompt
) {
}
