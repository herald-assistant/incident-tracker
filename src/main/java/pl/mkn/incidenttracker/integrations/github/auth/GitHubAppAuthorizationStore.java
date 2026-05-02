package pl.mkn.incidenttracker.integrations.github.auth;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface GitHubAppAuthorizationStore {

    Optional<GitHubAppAuthorization> findActiveByOperatorSessionId(String operatorSessionId);

    GitHubAppAuthorization save(GitHubAppAuthorization authorization);

    GitHubAppAuthorization update(String operatorSessionId, UnaryOperator<GitHubAppAuthorization> updater);

    void revoke(String operatorSessionId);
}
