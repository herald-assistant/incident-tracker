package pl.mkn.tdw.api.aiskills;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AiSkillCatalogMutationException extends UserFacingApplicationException {

    public AiSkillCatalogMutationException(String code, UserFacingErrorType errorType, String message) {
        super(code, errorType, message);
    }
}
