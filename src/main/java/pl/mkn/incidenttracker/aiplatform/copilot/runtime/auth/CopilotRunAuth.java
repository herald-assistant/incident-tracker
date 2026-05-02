package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import org.springframework.util.StringUtils;

public record CopilotRunAuth(
        CopilotAuthMode mode,
        String principalId,
        String githubLogin,
        boolean userBilling
) {

    public CopilotRunAuth {
        mode = mode != null ? mode : CopilotAuthMode.LOCAL_TOKEN;
        principalId = normalize(principalId);
        githubLogin = normalize(githubLogin);
    }

    public static CopilotRunAuth localToken() {
        return new CopilotRunAuth(CopilotAuthMode.LOCAL_TOKEN, "local-token", null, false);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
