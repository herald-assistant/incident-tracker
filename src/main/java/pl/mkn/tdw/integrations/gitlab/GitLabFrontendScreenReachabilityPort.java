package pl.mkn.tdw.integrations.gitlab;

import java.util.List;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendScreenSelectionRequest;

public interface GitLabFrontendScreenReachabilityPort {

    GitLabFrontendScreenReachabilityGraph build(GitLabFrontendScreenSelectionRequest request);

    GitLabFrontendScreenReachabilityGraph buildFocused(
            GitLabFrontendScreenSelectionRequest request,
            List<String> componentSelectors
    );
}
