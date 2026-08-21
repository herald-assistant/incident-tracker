package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerReachabilityInputException extends UserFacingApplicationException {

    public UiExplorerReachabilityInputException(String message) {
        super("UI_EXPLORER_REACHABILITY_INPUT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
