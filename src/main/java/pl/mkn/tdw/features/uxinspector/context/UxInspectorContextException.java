package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UxInspectorContextException extends UserFacingApplicationException {
    public UxInspectorContextException(String code, UserFacingErrorType type, String message) {
        super(code, type, message);
    }
}

