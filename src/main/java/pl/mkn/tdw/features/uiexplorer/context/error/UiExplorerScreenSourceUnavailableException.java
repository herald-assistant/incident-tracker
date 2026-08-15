package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerScreenSourceUnavailableException extends UserFacingApplicationException {

    public UiExplorerScreenSourceUnavailableException(String systemId, String screenId) {
        super(
                "UI_EXPLORER_SCREEN_SOURCE_UNAVAILABLE",
                UserFacingErrorType.UNPROCESSABLE_ENTITY,
                "Selected screen source could not be resolved for " + systemId + ": " + screenId
        );
    }
}
