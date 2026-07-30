package pl.mkn.tdw.features.runtimeconfigurationverification.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class RuntimeConfigurationVerificationJobNotFoundException extends UserFacingApplicationException {

    public RuntimeConfigurationVerificationJobNotFoundException(String jobId) {
        super(
                "RUNTIME_CONFIGURATION_VERIFICATION_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Runtime Configuration Verification job not found: " + jobId
        );
    }
}
