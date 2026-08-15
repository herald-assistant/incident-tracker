package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendRouteCatalogRequest(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendDiscoveryLimits limits
) {

    public GitLabFrontendRouteCatalogRequest {
        if (scope == null) {
            throw new IllegalArgumentException("scope must be provided");
        }
        limits = limits != null ? limits : GitLabFrontendDiscoveryLimits.defaults();
    }
}

