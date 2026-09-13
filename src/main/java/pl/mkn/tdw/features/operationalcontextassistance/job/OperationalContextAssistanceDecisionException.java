package pl.mkn.tdw.features.operationalcontextassistance.job;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public final class OperationalContextAssistanceDecisionException extends UserFacingApplicationException {
    public OperationalContextAssistanceDecisionException(String code, UserFacingErrorType type, String message) {
        super(code, type, message);
    }
}
