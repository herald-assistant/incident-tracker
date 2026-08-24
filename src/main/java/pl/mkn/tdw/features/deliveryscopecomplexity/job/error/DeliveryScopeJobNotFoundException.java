package pl.mkn.tdw.features.deliveryscopecomplexity.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryScopeJobNotFoundException extends UserFacingApplicationException {

    public DeliveryScopeJobNotFoundException(String jobId) {
        super("DELIVERY_SCOPE_JOB_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "Delivery assessment job was not found: " + jobId);
    }
}
