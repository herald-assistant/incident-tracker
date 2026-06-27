package pl.mkn.tdw.features.incidentanalysis.flow;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        InitialAnalysisRequest aiRequest,
        String preparedPrompt,
        InitialAnalysisResponse aiResponse,
        AnalysisResultResponse result
) {
}
