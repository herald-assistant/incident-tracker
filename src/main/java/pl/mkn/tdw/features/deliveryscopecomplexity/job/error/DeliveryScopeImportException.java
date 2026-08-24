package pl.mkn.tdw.features.deliveryscopecomplexity.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryScopeImportException extends UserFacingApplicationException {

    public DeliveryScopeImportException(String message) {
        super("DELIVERY_SCOPE_IMPORT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
