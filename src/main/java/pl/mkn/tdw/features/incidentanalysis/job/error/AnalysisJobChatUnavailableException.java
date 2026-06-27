package pl.mkn.tdw.features.incidentanalysis.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AnalysisJobChatUnavailableException extends UserFacingApplicationException {

    public AnalysisJobChatUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
