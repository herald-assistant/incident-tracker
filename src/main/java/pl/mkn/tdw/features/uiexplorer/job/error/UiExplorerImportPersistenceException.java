package pl.mkn.tdw.features.uiexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerImportPersistenceException extends UserFacingApplicationException {

    public UiExplorerImportPersistenceException() {
        super(
                "UI_EXPLORER_IMPORT_PERSISTENCE_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "UI Explorer import cannot be saved in local Analysis History."
        );
    }
}
