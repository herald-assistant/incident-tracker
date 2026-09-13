package pl.mkn.tdw.features.operationalcontextassistance.source;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public final class OperationalContextGitLabSourceSelectionException extends UserFacingApplicationException {

    public OperationalContextGitLabSourceSelectionException(String message) {
        super("OPCTX_ASSISTANCE_INVALID_GITLAB_SOURCE", UserFacingErrorType.BAD_REQUEST, message);
    }

    private OperationalContextGitLabSourceSelectionException(
            String code, UserFacingErrorType type, String message
    ) {
        super(code, type, message);
    }

    public static OperationalContextGitLabSourceSelectionException missingConfiguration() {
        return new OperationalContextGitLabSourceSelectionException(
                "OPCTX_ASSISTANCE_GITLAB_NOT_CONFIGURED", UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Nie skonfigurowano adresu lub głównej grupy GitLab dla asysty Operational Context."
        );
    }
}
