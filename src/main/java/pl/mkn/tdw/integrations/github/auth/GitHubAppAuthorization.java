package pl.mkn.tdw.integrations.github.auth;

import java.time.Instant;

public record GitHubAppAuthorization(
        String operatorSessionId,
        Long githubUserId,
        String githubLogin,
        String encryptedAccessToken,
        Instant accessTokenExpiresAt,
        String encryptedRefreshToken,
        Instant refreshTokenExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant lastUsedAt,
        Instant revokedAt
) {

    public boolean active() {
        return revokedAt == null;
    }

    @Override
    public String toString() {
        return "GitHubAppAuthorization[operatorSessionId=%s, githubUserId=%s, githubLogin=%s, accessToken=<encrypted>, refreshToken=<encrypted>, accessTokenExpiresAt=%s, refreshTokenExpiresAt=%s, createdAt=%s, updatedAt=%s, lastUsedAt=%s, revokedAt=%s]"
                .formatted(
                        operatorSessionId,
                        githubUserId,
                        githubLogin,
                        accessTokenExpiresAt,
                        refreshTokenExpiresAt,
                        createdAt,
                        updatedAt,
                        lastUsedAt,
                        revokedAt
                );
    }
}
