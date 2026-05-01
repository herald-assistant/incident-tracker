package pl.mkn.incidenttracker.features.incidentanalysis.flow;

public class AnalysisDataNotFoundException extends RuntimeException {

    public AnalysisDataNotFoundException(String correlationId) {
        super("No diagnostic data found for correlationId: " + correlationId);
    }

}
