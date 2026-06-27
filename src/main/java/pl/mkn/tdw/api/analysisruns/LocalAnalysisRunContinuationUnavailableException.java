package pl.mkn.tdw.api.analysisruns;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class LocalAnalysisRunContinuationUnavailableException extends UserFacingApplicationException {

    public LocalAnalysisRunContinuationUnavailableException(String message) {
        super(
                "LOCAL_ANALYSIS_RUN_CONTINUATION_UNAVAILABLE",
                UserFacingErrorType.CONFLICT,
                message
        );
    }
}
