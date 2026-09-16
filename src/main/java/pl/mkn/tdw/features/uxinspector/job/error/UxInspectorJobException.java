package pl.mkn.tdw.features.uxinspector.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UxInspectorJobException extends UserFacingApplicationException {
    public UxInspectorJobException(String code, UserFacingErrorType type, String message) { super(code, type, message); }
}

