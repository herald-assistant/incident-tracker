package pl.mkn.incidenttracker.api.githubauth;

import java.time.Instant;

public record GitHubAuthStatusResponse(
        String mode,
        boolean required,
        boolean connected,
        String githubLogin,
        String displayName,
        Instant tokenExpiresAt,
        boolean reauthRequired,
        String authStartUrl
) {
}
