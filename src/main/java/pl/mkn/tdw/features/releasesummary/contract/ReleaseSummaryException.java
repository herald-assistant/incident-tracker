package pl.mkn.tdw.features.releasesummary.contract;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;
public class ReleaseSummaryException extends UserFacingApplicationException {
    public ReleaseSummaryException(String code, String message) {
        super(code, switch (code) {
            case "JOB_NOT_FOUND" -> UserFacingErrorType.NOT_FOUND;
            case "INVALID_REF_QUERY", "INVALID_IMPORT" -> UserFacingErrorType.BAD_REQUEST;
            case "FEATURE_DISABLED" -> UserFacingErrorType.SERVICE_UNAVAILABLE;
            case "JOB_BUSY" -> UserFacingErrorType.CONFLICT;
            default -> UserFacingErrorType.UNPROCESSABLE_ENTITY;
        }, message);
    }
}

