package pl.mkn.incidenttracker.features.incidentanalysis.flow;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        InitialAnalysisRequest aiRequest,
        String preparedPrompt,
        InitialAnalysisResponse aiResponse,
        AnalysisResultResponse result
) {
}
