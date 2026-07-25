package pl.mkn.tdw.features.changeverification.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ChangeVerificationJobNotFoundException extends UserFacingApplicationException {

    public ChangeVerificationJobNotFoundException(String jobId) {
        super(
                "CHANGE_VERIFICATION_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Change Verification job not found: " + jobId
        );
    }
}
