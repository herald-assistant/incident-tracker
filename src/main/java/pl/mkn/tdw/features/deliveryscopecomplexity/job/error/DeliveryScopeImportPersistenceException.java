package pl.mkn.tdw.features.deliveryscopecomplexity.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryScopeImportPersistenceException extends UserFacingApplicationException {

    public DeliveryScopeImportPersistenceException() {
        super(
                "DELIVERY_SCOPE_IMPORT_PERSISTENCE_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Delivery Scope Complexity import cannot be saved in local Analysis History."
        );
    }
}
