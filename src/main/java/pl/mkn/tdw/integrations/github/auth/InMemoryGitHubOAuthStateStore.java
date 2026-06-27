package pl.mkn.tdw.integrations.github.auth;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGitHubOAuthStateStore implements GitHubOAuthStateStore {

    private final Map<String, GitHubOAuthState> states = new ConcurrentHashMap<>();

    @Override
    public void save(GitHubOAuthState state) {
        states.put(state.state(), state);
    }

    @Override
    public GitHubOAuthState consume(String state, String operatorSessionId, Instant now) {
        if (!StringUtils.hasText(state)) {
            throw new GitHubOAuthStateInvalidException("OAuth state is missing.");
        }

        var stored = states.remove(state);
        if (stored == null) {
            throw new GitHubOAuthStateInvalidException("OAuth state is invalid or already used.");
        }
        if (!stored.operatorSessionId().equals(operatorSessionId)) {
            throw new GitHubOAuthStateInvalidException("OAuth state does not belong to this operator session.");
        }
        if (stored.expiresAt().isBefore(now)) {
            throw new GitHubOAuthStateInvalidException("OAuth state has expired.");
        }

        return stored;
    }
}
