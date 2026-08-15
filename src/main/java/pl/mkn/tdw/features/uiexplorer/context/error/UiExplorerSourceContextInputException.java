package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerSourceContextInputException extends UserFacingApplicationException {

    public UiExplorerSourceContextInputException(String message) {
        super("UI_EXPLORER_SOURCE_CONTEXT_INPUT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
