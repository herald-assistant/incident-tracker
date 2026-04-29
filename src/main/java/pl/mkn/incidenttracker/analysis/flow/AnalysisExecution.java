package pl.mkn.incidenttracker.analysis.flow;
import pl.mkn.incidenttracker.analysis.ai.analysis.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.analysis.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        AnalysisAiAnalysisRequest aiRequest,
        String preparedPrompt,
        AnalysisAiAnalysisResponse aiResponse,
        AnalysisResultResponse result
) {
}
