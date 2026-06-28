package pl.mkn.tdw.features.flowexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class FlowExplorerJobResultUpdateUnavailableException extends UserFacingApplicationException {

    public FlowExplorerJobResultUpdateUnavailableException(String code, String message) {
        super(code, UserFacingErrorType.CONFLICT, message);
    }
}
