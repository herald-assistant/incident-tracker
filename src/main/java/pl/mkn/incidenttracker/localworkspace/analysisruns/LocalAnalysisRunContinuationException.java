package pl.mkn.incidenttracker.localworkspace.analysisruns;

public class LocalAnalysisRunContinuationException extends RuntimeException {

    private final Reason reason;

    private LocalAnalysisRunContinuationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static LocalAnalysisRunContinuationException unavailable(String message) {
        return new LocalAnalysisRunContinuationException(Reason.UNAVAILABLE, message, null);
    }

    public static LocalAnalysisRunContinuationException corrupted(String message, Throwable cause) {
        return new LocalAnalysisRunContinuationException(Reason.CORRUPTED, message, cause);
    }

    public static LocalAnalysisRunContinuationException chatFailed(String message, Throwable cause) {
        return new LocalAnalysisRunContinuationException(Reason.CHAT_FAILED, message, cause);
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        UNAVAILABLE,
        CORRUPTED,
        CHAT_FAILED
    }
}
