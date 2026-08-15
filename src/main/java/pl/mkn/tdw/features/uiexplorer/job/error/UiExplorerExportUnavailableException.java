package pl.mkn.tdw.features.uiexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerExportUnavailableException extends UserFacingApplicationException {

    public UiExplorerExportUnavailableException(String message) {
        super("UI_EXPLORER_EXPORT_UNAVAILABLE", UserFacingErrorType.CONFLICT, message);
    }
}
