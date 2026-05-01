package pl.mkn.incidenttracker.features.incidentanalysis.job.state;

public enum AnalysisJobStatus {
    QUEUED,
    COLLECTING_EVIDENCE,
    ANALYZING,
    COMPLETED,
    NOT_FOUND,
    FAILED
}
