package pl.mkn.tdw.features.systemassistant.contract;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class SystemAssistantException extends UserFacingApplicationException {
    public SystemAssistantException(String code, UserFacingErrorType type, String message) {
        super(code, type, message);
    }
}
