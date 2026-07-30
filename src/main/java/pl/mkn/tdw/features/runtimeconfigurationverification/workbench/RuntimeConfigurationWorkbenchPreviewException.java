package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class RuntimeConfigurationWorkbenchPreviewException extends UserFacingApplicationException {

    public RuntimeConfigurationWorkbenchPreviewException() {
        super(
                "RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Runtime configuration preview did not complete. Check source coverage and retry."
        );
    }
}
