package pl.mkn.incidenttracker.analysis.job;

public enum AnalysisJobStatus {
    QUEUED,
    COLLECTING_EVIDENCE,
    ANALYZING,
    COMPLETED,
    NOT_FOUND,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == NOT_FOUND || this == FAILED;
    }
}
