package pl.mkn.tdw.features.operationalcontextassistance.api;

public enum OperationalContextAssistanceJobStatus {
    QUEUED,
    COLLECTING_CONTEXT,
    AI_PREPARATION,
    ANALYZING,
    COMPLETED,
    PARTIAL,
    BLOCKED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == PARTIAL || this == BLOCKED || this == FAILED;
    }
}
