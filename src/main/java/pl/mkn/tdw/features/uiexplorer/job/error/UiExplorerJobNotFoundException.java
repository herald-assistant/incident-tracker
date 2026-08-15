package pl.mkn.tdw.features.uiexplorer.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class UiExplorerJobNotFoundException extends UserFacingApplicationException {

    public UiExplorerJobNotFoundException(String jobId) {
        super(
                "UI_EXPLORER_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "UI Explorer job not found: " + jobId
        );
    }
}

