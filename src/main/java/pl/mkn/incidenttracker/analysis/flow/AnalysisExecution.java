package pl.mkn.incidenttracker.analysis.flow;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        InitialAnalysisRequest aiRequest,
        String preparedPrompt,
        InitialAnalysisResponse aiResponse,
        AnalysisResultResponse result
) {
}
