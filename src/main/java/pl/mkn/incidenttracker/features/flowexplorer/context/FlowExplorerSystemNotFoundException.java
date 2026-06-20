package pl.mkn.incidenttracker.features.flowexplorer.context;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class FlowExplorerSystemNotFoundException extends UserFacingApplicationException {

    public FlowExplorerSystemNotFoundException(String systemId) {
        super(
                "FLOW_EXPLORER_SYSTEM_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Flow Explorer system not found: " + systemId
        );
    }
}
