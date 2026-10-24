package pl.mkn.tdw.api.operationalcontext;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public final class OperationalContextQuestionStatusException extends UserFacingApplicationException {

    public OperationalContextQuestionStatusException() {
        super("OPERATIONAL_CONTEXT_INVALID_QUESTION_STATUS", UserFacingErrorType.BAD_REQUEST,
                "Question status must be open, resolved or all");
    }
}
