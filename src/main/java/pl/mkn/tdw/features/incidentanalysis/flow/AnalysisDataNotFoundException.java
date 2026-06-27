package pl.mkn.tdw.features.incidentanalysis.flow;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AnalysisDataNotFoundException extends UserFacingApplicationException {

    public AnalysisDataNotFoundException(String correlationId) {
        super(
                "ANALYSIS_DATA_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "No diagnostic data found for correlationId: " + correlationId
        );
    }

}
