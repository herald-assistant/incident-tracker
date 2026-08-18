package pl.mkn.tdw.features.deliverycomplexityassessment.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryAssessmentImportException extends UserFacingApplicationException {

    public DeliveryAssessmentImportException(String message) {
        super("DELIVERY_ASSESSMENT_IMPORT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
