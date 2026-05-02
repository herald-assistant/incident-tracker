package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import java.time.Instant;

public record CopilotAccessToken(
        String value,
        String githubLogin,
        Instant expiresAt,
        boolean userBound
) {

    @Override
    public String toString() {
        return "CopilotAccessToken[value=<redacted>, githubLogin=%s, expiresAt=%s, userBound=%s]"
                .formatted(githubLogin, expiresAt, userBound);
    }
}
