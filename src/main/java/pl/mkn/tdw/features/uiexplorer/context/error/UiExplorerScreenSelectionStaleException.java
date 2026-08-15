package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerScreenSelectionStaleException extends UserFacingApplicationException {

    public UiExplorerScreenSelectionStaleException(String systemId, String screenId) {
        super(
                "UI_EXPLORER_SCREEN_SELECTION_STALE",
                UserFacingErrorType.CONFLICT,
                "Selected screen is no longer present in the current catalog for " + systemId
                        + ": " + screenId + ". Refresh the screen catalog and select the screen again."
        );
    }
}
