package pl.mkn.incidenttracker.analysis.flow;

import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagram;
import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;
import pl.mkn.incidenttracker.analysis.AnalysisVariantStatus;

public record AnalysisVariantResultResponse(
        AnalysisMode mode,
        AnalysisVariantStatus status,
        String detectedProblem,
        String summary,
        String recommendedAction,
        String rationale,
        AnalysisProblemNature problemNature,
        AnalysisConfidence confidence,
        String prompt,
        AnalysisFlowDiagram diagram
) {
}
