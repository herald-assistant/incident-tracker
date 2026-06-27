package pl.mkn.incidenttracker.api.analysisruns;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class LocalAnalysisRunChatFailedException extends UserFacingApplicationException {

    public LocalAnalysisRunChatFailedException(String message) {
        super(
                "LOCAL_ANALYSIS_RUN_CHAT_FAILED",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                message
        );
    }
}
