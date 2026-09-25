package pl.mkn.tdw.shared.ai.report;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AnalysisReportEditException extends UserFacingApplicationException {
    public AnalysisReportEditException(String code, UserFacingErrorType type, String message) {
        super(code, type, message);
    }
}
