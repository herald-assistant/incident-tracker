package pl.mkn.tdw.integrations.httpauth.contract;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class HttpAuthException extends UserFacingApplicationException {
    public HttpAuthException(String message) {
        super("HTTP_AUTH_FAILED", UserFacingErrorType.BAD_REQUEST, message);
    }
}
