package pl.mkn.incidenttracker.api.analysisruns;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class LocalAnalysisRunContinuationUnavailableException extends UserFacingApplicationException {

    public LocalAnalysisRunContinuationUnavailableException(String message) {
        super(
                "LOCAL_ANALYSIS_RUN_CONTINUATION_UNAVAILABLE",
                UserFacingErrorType.CONFLICT,
                message
        );
    }
}
