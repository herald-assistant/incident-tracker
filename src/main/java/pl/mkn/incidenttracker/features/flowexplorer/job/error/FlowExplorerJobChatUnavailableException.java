package pl.mkn.incidenttracker.features.flowexplorer.job.error;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class FlowExplorerJobChatUnavailableException extends UserFacingApplicationException {

    public FlowExplorerJobChatUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
