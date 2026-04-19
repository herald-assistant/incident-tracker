package pl.mkn.incidenttracker.analysis.ai;

import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;

public record AnalysisAiPriorResult(
        String detectedProblem,
        String summary,
        String recommendedAction,
        String rationale,
        AnalysisProblemNature problemNature,
        AnalysisConfidence confidence
) {
}
