package pl.mkn.tdw.features.releasesummary.contract;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;
public class ReleaseSummaryException extends UserFacingApplicationException {
    public ReleaseSummaryException(String code, String message) {
        super(code, UserFacingErrorType.UNPROCESSABLE_ENTITY, message);
    }
}

