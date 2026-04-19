package pl.mkn.incidenttracker.analysis.flow;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

public record AnalysisExecution(
        AnalysisContext context,
        AnalysisResultResponse result
) {
}
