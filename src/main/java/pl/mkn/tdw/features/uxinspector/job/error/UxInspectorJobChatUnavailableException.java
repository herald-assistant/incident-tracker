package pl.mkn.tdw.features.uxinspector.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UxInspectorJobChatUnavailableException extends UserFacingApplicationException {
    public UxInspectorJobChatUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
