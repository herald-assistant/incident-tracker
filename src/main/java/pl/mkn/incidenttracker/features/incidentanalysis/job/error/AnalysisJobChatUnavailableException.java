package pl.mkn.incidenttracker.features.incidentanalysis.job.error;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class AnalysisJobChatUnavailableException extends UserFacingApplicationException {

    public AnalysisJobChatUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
