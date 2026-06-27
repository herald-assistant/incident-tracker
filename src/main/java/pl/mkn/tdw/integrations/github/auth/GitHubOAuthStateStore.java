package pl.mkn.tdw.integrations.github.auth;

import java.time.Instant;

public interface GitHubOAuthStateStore {

    void save(GitHubOAuthState state);

    GitHubOAuthState consume(String state, String operatorSessionId, Instant now);
}
