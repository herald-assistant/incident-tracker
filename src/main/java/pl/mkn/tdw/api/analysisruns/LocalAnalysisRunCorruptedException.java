package pl.mkn.tdw.api.analysisruns;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class LocalAnalysisRunCorruptedException extends UserFacingApplicationException {

    public LocalAnalysisRunCorruptedException(String analysisId) {
        super(
                "LOCAL_ANALYSIS_RUN_CORRUPTED",
                UserFacingErrorType.CONFLICT,
                "Local analysis run cannot be read: " + analysisId
        );
    }
}
