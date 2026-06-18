package pl.mkn.incidenttracker.features.flowexplorer.job.error;

public class FlowExplorerJobChatUnavailableException extends RuntimeException {

    private final String code;

    public FlowExplorerJobChatUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
