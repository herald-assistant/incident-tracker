package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class RuntimeConfigurationWorkbenchPreviewNotFoundException
        extends UserFacingApplicationException {

    public RuntimeConfigurationWorkbenchPreviewNotFoundException() {
        super(
                "RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Runtime configuration preview is missing or expired. Run a new preview."
        );
    }
}
