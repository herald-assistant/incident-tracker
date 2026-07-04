package pl.mkn.tdw.features.incidentanalysis.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AnalysisJobInputException extends UserFacingApplicationException {

    public AnalysisJobInputException(String code, String message) {
        super(code, UserFacingErrorType.BAD_REQUEST, message);
    }
}
