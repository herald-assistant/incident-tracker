package pl.mkn.tdw.features.uiexplorer.catalog.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerFrontendNotEligibleException extends UserFacingApplicationException {

    public UiExplorerFrontendNotEligibleException(String systemId) {
        super(
                "UI_EXPLORER_FRONTEND_NOT_ELIGIBLE",
                UserFacingErrorType.UNPROCESSABLE_ENTITY,
                "System is not registered as an eligible UI Explorer frontend: " + systemId
        );
    }
}
