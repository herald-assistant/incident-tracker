package pl.mkn.tdw.features.deliverycomplexityassessment.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryAssessmentJobNotFoundException extends UserFacingApplicationException {

    public DeliveryAssessmentJobNotFoundException(String jobId) {
        super("DELIVERY_ASSESSMENT_JOB_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "Delivery assessment job was not found: " + jobId);
    }
}
