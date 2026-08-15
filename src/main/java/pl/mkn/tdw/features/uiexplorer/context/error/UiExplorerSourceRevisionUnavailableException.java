package pl.mkn.tdw.features.uiexplorer.context.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerSourceRevisionUnavailableException extends UserFacingApplicationException {

    public UiExplorerSourceRevisionUnavailableException(String systemId, String ref) {
        super(
                "UI_EXPLORER_SOURCE_REVISION_UNAVAILABLE",
                UserFacingErrorType.UNPROCESSABLE_ENTITY,
                "Exact source revision is unavailable for " + systemId + " at ref " + ref + "."
        );
    }
}
