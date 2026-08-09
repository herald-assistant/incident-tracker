package pl.mkn.tdw.features.configdriftviewer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ConfigDriftViewerImportException extends UserFacingApplicationException {

    public ConfigDriftViewerImportException(String message) {
        super(
                "RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID",
                UserFacingErrorType.BAD_REQUEST,
                message
        );
    }
}
