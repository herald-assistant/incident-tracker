package pl.mkn.tdw.testsupport.integrations;

import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.internal.GitLabRepositoryAnalysisCache;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryTreeService;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.adapter.rest.GitLabRestRepositoryAdapter;
import pl.mkn.tdw.integrations.gitlab.service.source.GitLabSourceResolveService;

public final class GitLabIntegrationTestCreator {

    private GitLabIntegrationTestCreator() {
    }

    public static GitLabRepositoryEndpointService endpointService(GitLabRepositoryPort gitLabRepositoryPort) {
        return new GitLabRepositoryEndpointService(
                gitLabRepositoryPort,
                new GitLabRepositoryAnalysisCache()
        );
    }

    public static GitLabRestRepositoryAdapter repositoryAdapter(
            GitLabProperties gitLabProperties,
            GitLabRestClientFactory gitLabRestClientFactory
    ) {
        return new GitLabRestRepositoryAdapter(
                gitLabProperties,
                gitLabRestClientFactory,
                new GitLabRepositoryTreeService(gitLabRestClientFactory),
                new GitLabRepositoryAnalysisCache()
        );
    }

    public static GitLabSourceResolveService sourceResolveService(
            GitLabRestClientFactory gitLabRestClientFactory,
            GitLabProperties gitLabProperties
    ) {
        return new GitLabSourceResolveService(
                gitLabRestClientFactory,
                new GitLabRepositoryTreeService(gitLabRestClientFactory),
                gitLabProperties
        );
    }
}
