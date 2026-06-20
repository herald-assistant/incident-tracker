package pl.mkn.incidenttracker.features.flowexplorer.job.error;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class FlowExplorerJobNotFoundException extends UserFacingApplicationException {

    public FlowExplorerJobNotFoundException(String jobId) {
        super(
                "FLOW_EXPLORER_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Flow Explorer job not found: " + jobId
        );
    }
}
