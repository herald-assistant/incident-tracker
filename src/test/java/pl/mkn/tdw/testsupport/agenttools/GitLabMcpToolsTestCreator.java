package pl.mkn.tdw.testsupport.agenttools;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabMcpTools;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import static pl.mkn.tdw.testsupport.integrations.GitLabIntegrationTestCreator.endpointService;

public final class GitLabMcpToolsTestCreator {

    private GitLabMcpToolsTestCreator() {
    }

    public static GitLabMcpTools create(GitLabRepositoryPort gitLabRepositoryPort) {
        return create(gitLabRepositoryPort, new GitLabProperties());
    }

    public static GitLabMcpTools create(
            GitLabRepositoryPort gitLabRepositoryPort,
            GitLabProperties gitLabProperties
    ) {
        return create(gitLabRepositoryPort, ignored -> OperationalContextCatalog.empty(), gitLabProperties);
    }

    public static GitLabMcpTools create(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabProperties gitLabProperties
    ) {
        var gitLabRepositoryEndpointService = endpointService(gitLabRepositoryPort);
        return create(
                gitLabRepositoryPort,
                operationalContextPort,
                gitLabRepositoryEndpointService,
                GitLabEndpointUseCaseContextService.createDefault(
                        gitLabRepositoryPort,
                        gitLabRepositoryEndpointService
                ),
                GitLabJavaMethodUseCaseContextService.createDefault(gitLabRepositoryPort),
                new GitLabJavaMethodSliceService(gitLabRepositoryPort),
                new GitLabOpenApiEndpointSliceService(gitLabRepositoryPort, new ObjectMapper()),
                gitLabProperties
        );
    }

    public static GitLabMcpTools create(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService,
            GitLabProperties gitLabProperties
    ) {
        return create(
                gitLabRepositoryPort,
                operationalContextPort,
                gitLabRepositoryEndpointService,
                GitLabEndpointUseCaseContextService.createDefault(
                        gitLabRepositoryPort,
                        gitLabRepositoryEndpointService
                ),
                GitLabJavaMethodUseCaseContextService.createDefault(gitLabRepositoryPort),
                new GitLabJavaMethodSliceService(gitLabRepositoryPort),
                new GitLabOpenApiEndpointSliceService(gitLabRepositoryPort, new ObjectMapper()),
                gitLabProperties
        );
    }

    public static GitLabMcpTools create(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService,
            GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService,
            GitLabJavaMethodUseCaseContextService gitLabJavaMethodUseCaseContextService,
            GitLabJavaMethodSliceService gitLabJavaMethodSliceService,
            GitLabOpenApiEndpointSliceService gitLabOpenApiEndpointSliceService,
            GitLabProperties gitLabProperties
    ) {
        return new GitLabMcpTools(
                gitLabRepositoryPort,
                operationalContextPort,
                gitLabRepositoryEndpointService,
                gitLabEndpointUseCaseContextService,
                gitLabJavaMethodUseCaseContextService,
                gitLabJavaMethodSliceService,
                gitLabOpenApiEndpointSliceService,
                gitLabProperties
        );
    }
}
