package pl.mkn.tdw.features.systemassistant.contract;

import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class SystemAssistantLoginRequiredException extends SystemAssistantException {
    private final String authProfileId;

    public SystemAssistantLoginRequiredException(String authProfileId) {
        super("AUTH_LOGIN_REQUIRED", UserFacingErrorType.CONFLICT,
                "Sesja API wygasła lub wymaga logowania. Wiadomość nie została przyjęta.");
        this.authProfileId = authProfileId;
    }

    public String authProfileId() { return authProfileId; }
}
