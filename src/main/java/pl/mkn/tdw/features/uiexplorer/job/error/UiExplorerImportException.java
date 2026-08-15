package pl.mkn.tdw.features.uiexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerImportException extends UserFacingApplicationException {

    public UiExplorerImportException(String message) {
        super("UI_EXPLORER_IMPORT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
