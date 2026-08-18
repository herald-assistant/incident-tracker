package pl.mkn.tdw.features.deliverycomplexityassessment.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryAssessmentImportPersistenceException extends UserFacingApplicationException {

    public DeliveryAssessmentImportPersistenceException() {
        super(
                "DELIVERY_ASSESSMENT_IMPORT_PERSISTENCE_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Delivery Complexity Assessment import cannot be saved in local Analysis History."
        );
    }
}
