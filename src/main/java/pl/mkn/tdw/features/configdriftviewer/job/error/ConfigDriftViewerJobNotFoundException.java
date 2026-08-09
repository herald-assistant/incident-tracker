package pl.mkn.tdw.features.configdriftviewer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ConfigDriftViewerJobNotFoundException extends UserFacingApplicationException {

    public ConfigDriftViewerJobNotFoundException(String jobId) {
        super(
                "RUNTIME_CONFIGURATION_VERIFICATION_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Config Drift Viewer job not found: " + jobId
        );
    }
}
