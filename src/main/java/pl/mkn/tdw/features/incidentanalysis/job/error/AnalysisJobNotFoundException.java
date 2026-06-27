package pl.mkn.tdw.features.incidentanalysis.job.error;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AnalysisJobNotFoundException extends UserFacingApplicationException {

    public AnalysisJobNotFoundException(String analysisId) {
        super(
                "ANALYSIS_JOB_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Analysis job not found: " + analysisId
        );
    }

}
