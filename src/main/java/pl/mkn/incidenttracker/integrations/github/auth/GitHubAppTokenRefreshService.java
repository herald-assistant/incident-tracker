package pl.mkn.incidenttracker.integrations.github.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GitHubAppTokenRefreshService {

    private final GitHubAppAuthProperties properties;
    private final GitHubAppOAuthClient oauthClient;
    private final GitHubAppAuthorizationStore authorizationStore;
    private final GitHubTokenCipher tokenCipher;

    public GitHubAppAuthorization ensureFresh(GitHubAppAuthorization authorization, Instant now) {
        if (authorization == null || !authorization.active()) {
            throw new GitHubAppAuthorizationReauthRequiredException("GitHub authorization is missing or revoked.");
        }

        var expiresAt = authorization.accessTokenExpiresAt();
        if (expiresAt == null || expiresAt.isAfter(now.plus(refreshSkew()))) {
            return markLastUsed(authorization.operatorSessionId(), now);
        }

        if (!StringUtils.hasText(authorization.encryptedRefreshToken())
                || (authorization.refreshTokenExpiresAt() != null && authorization.refreshTokenExpiresAt().isBefore(now))) {
            throw new GitHubAppAuthorizationReauthRequiredException("GitHub refresh token is missing or expired.");
        }

        var refreshToken = tokenCipher.decrypt(authorization.encryptedRefreshToken());
        var tokenResponse = oauthClient.refresh(refreshToken);

        return authorizationStore.update(authorization.operatorSessionId(), current -> new GitHubAppAuthorization(
                current.operatorSessionId(),
                current.githubUserId(),
                current.githubLogin(),
                tokenCipher.encrypt(tokenResponse.accessToken()),
                expiresAt(now, tokenResponse.expiresIn()),
                tokenCipher.encrypt(tokenResponse.refreshToken()),
                expiresAt(now, tokenResponse.refreshTokenExpiresIn()),
                current.createdAt(),
                now,
                now,
                null
        ));
    }

    private GitHubAppAuthorization markLastUsed(String operatorSessionId, Instant now) {
        return authorizationStore.update(operatorSessionId, current -> new GitHubAppAuthorization(
                current.operatorSessionId(),
                current.githubUserId(),
                current.githubLogin(),
                current.encryptedAccessToken(),
                current.accessTokenExpiresAt(),
                current.encryptedRefreshToken(),
                current.refreshTokenExpiresAt(),
                current.createdAt(),
                now,
                now,
                current.revokedAt()
        ));
    }

    private java.time.Duration refreshSkew() {
        return properties.getTokenRefreshSkew() != null
                ? properties.getTokenRefreshSkew()
                : java.time.Duration.ofMinutes(5);
    }

    private Instant expiresAt(Instant now, Long expiresInSeconds) {
        return expiresInSeconds != null ? now.plusSeconds(expiresInSeconds) : null;
    }
}
