package pl.mkn.tdw.integrations.github.auth;

import java.time.Instant;

public record GitHubOAuthState(
        String state,
        String operatorSessionId,
        String returnUrl,
        Instant createdAt,
        Instant expiresAt,
        String codeVerifier
) {
}
