package pl.mkn.incidenttracker.analysis.ai;

import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagram;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;

public record AnalysisAiAnalysisResponse(
        String providerName,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        AnalysisProblemNature problemNature,
        AnalysisConfidence confidence,
        String prompt,
        AnalysisFlowDiagram diagram
) {

    public AnalysisAiAnalysisResponse(
            String providerName,
            String summary,
            String detectedProblem,
            String recommendedAction,
            String rationale,
            AnalysisProblemNature problemNature,
            AnalysisConfidence confidence,
            String prompt
    ) {
        this(
                providerName,
                summary,
                detectedProblem,
                recommendedAction,
                rationale,
                problemNature,
                confidence,
                prompt,
                null
        );
    }
}
