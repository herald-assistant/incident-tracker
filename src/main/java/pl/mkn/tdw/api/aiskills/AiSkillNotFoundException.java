package pl.mkn.tdw.api.aiskills;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class AiSkillNotFoundException extends UserFacingApplicationException {

    public AiSkillNotFoundException(String skillName) {
        super(
                "AI_SKILL_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "AI skill not found: " + skillName
        );
    }
}
