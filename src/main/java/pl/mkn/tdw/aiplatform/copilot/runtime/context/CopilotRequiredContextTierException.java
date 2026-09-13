package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

/** A required Copilot context tier cannot be established before sending the prompt. */
public final class CopilotRequiredContextTierException extends UserFacingApplicationException {

    public CopilotRequiredContextTierException(String message) {
        super("COPILOT_LONG_CONTEXT_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE, message);
    }
}
