package pl.mkn.tdw.features.flowexplorer.endpoint;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.flowexplorer.FlowExplorerProperties;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryScopeService;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSystemNotFoundException;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointDocumentation;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointParameterDocumentation;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerEndpointInventoryServiceTest {

    @Test
    void shouldBuildEndpointInventoryForSystemUsingConfiguredDefaultBranch() {
        var endpointService = mock(GitLabRepositoryEndpointService.class);
        when(endpointService.listEndpoints(argThat(request -> request != null
                && "catalog-api".equals(request.projectName()))))
                .thenReturn(endpointList("catalog-api", List.of(endpoint()), List.of()));
        when(endpointService.listEndpoints(argThat(request -> request != null
                && "domains/catalog-domain".equals(request.projectName()))))
                .thenReturn(endpointList("domains/catalog-domain", List.of(), List.of("No controllers found.")));
        var service = service(endpointService, "platform/backend", "release-candidate");

        var response = service.endpoints("catalog-core", null, "/api", "get");

        assertEquals("catalog-core", response.systemId());
        assertEquals("release-candidate", response.resolvedRef());
        assertEquals("platform/backend", response.gitLabGroup());
        assertEquals("/api", response.endpointPathPrefix());
        assertEquals("GET", response.httpMethod());
        assertEquals(3, response.repositoryCount());
        assertEquals(2, response.scannedRepositoryCount());
        assertEquals(1, response.endpointCount());
        assertEquals(6, response.candidateFileCount());
        assertEquals(4, response.scannedFileCount());
        assertTrue(response.limitations().contains("Operational context references unknown repository: missing-repo"));
        assertTrue(response.limitations().contains("catalog-domain: No controllers found."));

        var endpoint = response.endpoints().get(0);
        assertEquals("catalog-api:GET /api/catalog/{id}", endpoint.endpointId());
        assertEquals("GET", endpoint.method());
        assertEquals("/api/catalog/{id}", endpoint.path());
        assertEquals("Returns catalog details.", endpoint.description());
        assertEquals("getCatalog", endpoint.operationId());
        assertEquals("CatalogController", endpoint.controllerClass());
        assertEquals("catalog-api", endpoint.source().repositoryId());
        assertEquals("src/main/java/com/example/catalog/CatalogController.java", endpoint.source().filePath());
        assertEquals("id", endpoint.parameters().get(0).name());
        assertEquals("catalog item id", endpoint.tooltipDetails().parameters().get(0).description());

        var requestCaptor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService, times(2)).listEndpoints(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "release-candidate".equals(request.branch())));
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "platform/backend".equals(request.group())));
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "/api".equals(request.endpointPathPrefix())));
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "GET".equals(request.httpMethod())));
    }

    @Test
    void shouldUseExplicitBranchWhenProvided() {
        var endpointService = mock(GitLabRepositoryEndpointService.class);
        when(endpointService.listEndpoints(any())).thenReturn(endpointList("any", List.of(), List.of()));
        var service = service(endpointService, "platform/backend", "release-candidate");

        var response = service.endpoints("catalog-core", "feature/FLOW-42", null, null);

        assertEquals("feature/FLOW-42", response.requestedBranch());
        assertEquals("feature/FLOW-42", response.resolvedRef());

        var requestCaptor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService, times(2)).listEndpoints(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "feature/FLOW-42".equals(request.branch())));
    }

    @Test
    void shouldAllowRepositoriesFromConfiguredGitLabSubgroups() {
        var endpointService = mock(GitLabRepositoryEndpointService.class);
        when(endpointService.listEndpoints(argThat(request -> request != null
                && "CRM".equals(request.group())
                && "PROCESSES/CRM_CUSTOMER_PROCESS".equals(request.projectName()))))
                .thenReturn(endpointList("PROCESSES/CRM_CUSTOMER_PROCESS", List.of(endpoint()), List.of()));
        var service = service(endpointService, "CRM", "main", subgroupCatalog());

        var response = service.endpoints("customer-process", null, null, null);

        assertEquals("CRM", response.gitLabGroup());
        assertEquals(1, response.repositoryCount());
        assertEquals(1, response.scannedRepositoryCount());
        assertEquals(1, response.endpointCount());

        var requestCaptor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(requestCaptor.capture());
        assertEquals("CRM", requestCaptor.getValue().group());
        assertEquals("PROCESSES/CRM_CUSTOMER_PROCESS", requestCaptor.getValue().projectName());
        assertEquals("main", requestCaptor.getValue().branch());
    }

    @Test
    void shouldFailWhenSystemDoesNotExist() {
        var service = service(mock(GitLabRepositoryEndpointService.class), "platform/backend", "main");

        assertThrows(
                FlowExplorerSystemNotFoundException.class,
                () -> service.endpoints("missing-system", null, null, null)
        );
    }

    @Test
    void shouldFailWhenGitLabGroupIsNotConfigured() {
        var service = service(mock(GitLabRepositoryEndpointService.class), null, "main");

        assertThrows(
                FlowExplorerGitLabConfigurationException.class,
                () -> service.endpoints("catalog-core", null, null, null)
        );
    }

    private static FlowExplorerEndpointInventoryService service(
            GitLabRepositoryEndpointService endpointService,
            String gitLabGroup,
            String defaultBranch
    ) {
        return service(endpointService, gitLabGroup, defaultBranch, catalog());
    }

    private static FlowExplorerEndpointInventoryService service(
            GitLabRepositoryEndpointService endpointService,
            String gitLabGroup,
            String defaultBranch,
            OperationalContextCatalog catalog
    ) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup(gitLabGroup);
        var flowExplorerProperties = new FlowExplorerProperties();
        flowExplorerProperties.setDefaultBranch(defaultBranch);
        var scopeService = new FlowExplorerRepositoryScopeService(
                query -> catalog,
                gitLabProperties,
                flowExplorerProperties
        );
        return new FlowExplorerEndpointInventoryService(
                scopeService,
                endpointService
        );
    }

    private static GitLabRepositoryEndpointListResult endpointList(
            String projectName,
            List<GitLabRepositoryEndpoint> endpoints,
            List<String> limitations
    ) {
        return new GitLabRepositoryEndpointListResult(
                "platform/backend",
                projectName,
                "release-candidate",
                "/api",
                "GET",
                3,
                2,
                false,
                endpoints,
                limitations
        );
    }

    private static GitLabRepositoryEndpoint endpoint() {
        return new GitLabRepositoryEndpoint(
                "GET /api/catalog/{id}",
                List.of("GET"),
                "/api/catalog/{id}",
                "/api/catalog/{id}",
                "CatalogController",
                "getCatalog",
                "src/main/java/com/example/catalog/CatalogController.java",
                12,
                24,
                List.of("@PathVariable String id"),
                List.of("CatalogResponse"),
                List.of("RestController", "GetMapping"),
                new GitLabRepositoryEndpointDocumentation(
                        "OPENAPI_YAML",
                        "Catalog lookup",
                        "Returns catalog details.",
                        "getCatalog",
                        List.of("catalog"),
                        List.of(new GitLabRepositoryEndpointParameterDocumentation(
                                "id",
                                "path",
                                true,
                                "string",
                                "catalog item id"
                        ))
                ),
                "high",
                List.of(),
                List.of("catalog-api:src/main/java/com/example/catalog/CatalogController.java")
        );
    }

    private static OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "catalog-core",
                        "name", "Catalog Core",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("catalog-api"))
                )),
                List.of(),
                List.of(
                        map(
                                "id", "catalog-api",
                                "name", "Catalog API",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "platform/backend",
                                        "projectPath", "platform/backend/catalog-api"
                                )
                        ),
                        map(
                                "id", "catalog-domain",
                                "name", "Catalog Domain",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "platform/backend",
                                        "projectPath", "platform/backend/domains/catalog-domain"
                                )
                        )
                ),
                List.of(map(
                        "id", "catalog-scope",
                        "name", "Catalog scope",
                        "target", map("type", "system", "id", "catalog-core"),
                        "repositories", List.of(
                                map("repoId", "missing-repo", "priority", 1),
                                map("repoId", "catalog-domain", "priority", 2)
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static OperationalContextCatalog subgroupCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "customer-process",
                        "name", "Customer Process",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("crm-customer-process-repo"))
                )),
                List.of(),
                List.of(map(
                        "id", "crm-customer-process-repo",
                        "name", "CRM Customer Process",
                        "git", map(
                                "provider", "gitlab",
                                "group", "CRM/PROCESSES",
                                "projectPath", "CRM/PROCESSES/CRM_CUSTOMER_PROCESS"
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
