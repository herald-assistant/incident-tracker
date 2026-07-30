package pl.mkn.tdw.features.runtimeconfigurationverification.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class RuntimeConfigurationVerificationImportException extends UserFacingApplicationException {

    public RuntimeConfigurationVerificationImportException(String message) {
        super(
                "RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID",
                UserFacingErrorType.BAD_REQUEST,
                message
        );
    }
}
