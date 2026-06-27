package pl.mkn.tdw.integrations.github.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Component
public class InMemoryGitHubAppAuthorizationStore implements GitHubAppAuthorizationStore {

    private final Map<String, GitHubAppAuthorization> authorizations = new ConcurrentHashMap<>();

    @Override
    public Optional<GitHubAppAuthorization> findActiveByOperatorSessionId(String operatorSessionId) {
        return Optional.ofNullable(authorizations.get(operatorSessionId))
                .filter(GitHubAppAuthorization::active);
    }

    @Override
    public GitHubAppAuthorization save(GitHubAppAuthorization authorization) {
        authorizations.put(authorization.operatorSessionId(), authorization);
        return authorization;
    }

    @Override
    public GitHubAppAuthorization update(String operatorSessionId, UnaryOperator<GitHubAppAuthorization> updater) {
        return authorizations.compute(operatorSessionId, (ignored, current) -> {
            if (current == null || !current.active()) {
                throw new GitHubAppAuthorizationReauthRequiredException("GitHub authorization is missing or revoked.");
            }
            return updater.apply(current);
        });
    }

    @Override
    public void revoke(String operatorSessionId) {
        update(operatorSessionId, current -> new GitHubAppAuthorization(
                current.operatorSessionId(),
                current.githubUserId(),
                current.githubLogin(),
                current.encryptedAccessToken(),
                current.accessTokenExpiresAt(),
                current.encryptedRefreshToken(),
                current.refreshTokenExpiresAt(),
                current.createdAt(),
                Instant.now(),
                current.lastUsedAt(),
                Instant.now()
        ));
    }
}
