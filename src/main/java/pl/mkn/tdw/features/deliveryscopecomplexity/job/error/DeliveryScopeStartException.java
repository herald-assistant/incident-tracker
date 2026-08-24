package pl.mkn.tdw.features.deliveryscopecomplexity.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryScopeStartException extends UserFacingApplicationException {

    public DeliveryScopeStartException(String code, String message, UserFacingErrorType type) {
        super(code, type, message);
    }
}
