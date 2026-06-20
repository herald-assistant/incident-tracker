package pl.mkn.incidenttracker.shared.error;

public abstract class UserFacingApplicationException extends RuntimeException {

    private final String code;
    private final UserFacingErrorType errorType;

    protected UserFacingApplicationException(String code, UserFacingErrorType errorType, String message) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    public String code() {
        return code;
    }

    public UserFacingErrorType errorType() {
        return errorType;
    }
}
