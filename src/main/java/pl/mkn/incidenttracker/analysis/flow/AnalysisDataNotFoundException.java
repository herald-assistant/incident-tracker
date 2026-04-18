package pl.mkn.incidenttracker.analysis.flow;

public class AnalysisDataNotFoundException extends RuntimeException {

    public AnalysisDataNotFoundException(String correlationId) {
        super("No diagnostic data found for correlationId: " + correlationId);
    }

}
