package pl.mkn.tdw.features.uiexplorer.catalog.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerSourceRefNotFoundException extends UserFacingApplicationException {

    public UiExplorerSourceRefNotFoundException(String systemId, String ref) {
        super(
                "UI_EXPLORER_SOURCE_REF_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Source ref does not exist for UI Explorer frontend " + systemId + ": " + ref
        );
    }
}
