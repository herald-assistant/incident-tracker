package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRouteGraph;

public interface GitLabFrontendRouteGraphPort {

    GitLabFrontendRouteGraph discover(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits
    );
}
