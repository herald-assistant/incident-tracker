package pl.mkn.tdw.integrations.openapi.contract;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class OpenApiException extends UserFacingApplicationException {
    public OpenApiException(String message) {
        super("OPENAPI_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
