package pl.mkn.incidenttracker.features.incidentanalysis.job.error;

public class AnalysisJobChatUnavailableException extends RuntimeException {

    private final String code;

    public AnalysisJobChatUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
