package pl.mkn.tdw.features.uiexplorer.catalog.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerScreenCatalogInputException extends UserFacingApplicationException {

    public UiExplorerScreenCatalogInputException(String message) {
        super("UI_EXPLORER_SCREEN_CATALOG_INPUT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
