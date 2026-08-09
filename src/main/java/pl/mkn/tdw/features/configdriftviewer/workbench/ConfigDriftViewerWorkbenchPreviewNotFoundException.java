package pl.mkn.tdw.features.configdriftviewer.workbench;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ConfigDriftViewerWorkbenchPreviewNotFoundException
        extends UserFacingApplicationException {

    public ConfigDriftViewerWorkbenchPreviewNotFoundException() {
        super(
                "RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Runtime configuration preview is missing or expired. Run a new preview."
        );
    }
}
