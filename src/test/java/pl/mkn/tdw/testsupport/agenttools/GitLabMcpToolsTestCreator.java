package pl.mkn.tdw.testsupport.agenttools;

import pl.mkn.tdw.integrations.gitlab.service.GitLabVerifiedRepositoryFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabMcpTools;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.service.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.service.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.service.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.service.usecase.GitLabJavaMethodUseCaseContextService;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
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
                new GitLabVerifiedRepositoryFileService(), gitLabRepositoryPort,
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
