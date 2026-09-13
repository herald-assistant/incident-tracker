package pl.mkn.tdw.features.operationalcontextassistance.job;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class OperationalContextAssistanceJobNotFoundException extends UserFacingApplicationException {
    public OperationalContextAssistanceJobNotFoundException(String jobId) {
        super("OPCTX_ASSISTANCE_JOB_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                "Operational Context assistance job not found: " + jobId);
    }
}
