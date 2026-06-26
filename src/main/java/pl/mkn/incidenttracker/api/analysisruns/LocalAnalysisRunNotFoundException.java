package pl.mkn.incidenttracker.api.analysisruns;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class LocalAnalysisRunNotFoundException extends UserFacingApplicationException {

    public LocalAnalysisRunNotFoundException(String analysisId) {
        super(
                "LOCAL_ANALYSIS_RUN_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Local analysis run not found: " + analysisId
        );
    }
}
