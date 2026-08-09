package pl.mkn.tdw.features.configdriftviewer.workbench;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ConfigDriftViewerWorkbenchPreviewException extends UserFacingApplicationException {

    public ConfigDriftViewerWorkbenchPreviewException() {
        super(
                "RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Runtime configuration preview did not complete. Check source coverage and retry."
        );
    }
}
