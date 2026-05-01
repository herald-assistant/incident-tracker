package pl.mkn.incidenttracker.features.incidentanalysis.job.error;

public class AnalysisJobNotFoundException extends RuntimeException {

    public AnalysisJobNotFoundException(String analysisId) {
        super("Analysis job not found: " + analysisId);
    }

}
