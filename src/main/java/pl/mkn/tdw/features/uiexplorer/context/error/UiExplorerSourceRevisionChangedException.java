package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerSourceRevisionChangedException extends UserFacingApplicationException {

    public UiExplorerSourceRevisionChangedException(String systemId, String ref) {
        super(
                "UI_EXPLORER_SOURCE_REVISION_CHANGED",
                UserFacingErrorType.CONFLICT,
                "Source revision changed after the screen catalog was loaded for " + systemId
                        + " at ref " + ref + ". Refresh the screen catalog and select the screen again."
        );
    }
}
