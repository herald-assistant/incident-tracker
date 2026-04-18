package pl.mkn.incidenttracker.analysis.job;

public class AnalysisJobNotFoundException extends RuntimeException {

    public AnalysisJobNotFoundException(String analysisId) {
        super("Analysis job not found: " + analysisId);
    }

}
