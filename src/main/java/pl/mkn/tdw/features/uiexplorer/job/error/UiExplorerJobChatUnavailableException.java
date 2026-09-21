package pl.mkn.tdw.features.uiexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerJobChatUnavailableException extends UserFacingApplicationException {
    public UiExplorerJobChatUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
