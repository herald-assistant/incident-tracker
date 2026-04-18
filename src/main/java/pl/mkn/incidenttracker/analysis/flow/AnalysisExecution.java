package pl.mkn.incidenttracker.analysis.flow;

import pl.mkn.incidenttracker.analysis.AnalysisResultResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        AnalysisAiAnalysisRequest aiRequest,
        String preparedPrompt,
        AnalysisAiAnalysisResponse aiResponse,
        AnalysisResultResponse result
) {
}
