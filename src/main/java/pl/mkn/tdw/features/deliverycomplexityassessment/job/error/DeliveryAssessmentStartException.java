package pl.mkn.tdw.features.deliverycomplexityassessment.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class DeliveryAssessmentStartException extends UserFacingApplicationException {

    public DeliveryAssessmentStartException(String code, String message, UserFacingErrorType type) {
        super(code, type, message);
    }
}
