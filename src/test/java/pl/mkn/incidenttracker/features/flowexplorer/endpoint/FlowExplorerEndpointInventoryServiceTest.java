package pl.mkn.incidenttracker.features.flowexplorer.endpoint;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.features.flowexplorer.FlowExplorerProperties;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryScopeService;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSystemNotFoundException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointDocumentation;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointParameterDocumentation;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

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
                && "billing-api".equals(request.projectName()))))
                .thenReturn(endpointList("billing-api", List.of(endpoint()), List.of()));
        when(endpointService.listEndpoints(argThat(request -> request != null
                && "domains/billing-domain".equals(request.projectName()))))
                .thenReturn(endpointList("domains/billing-domain", List.of(), List.of("No controllers found.")));
        var service = service(endpointService, "platform/backend", "release-candidate");

        var response = service.endpoints("billing-core", null, "/api", "get");

        assertEquals("billing-core", response.systemId());
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
        assertTrue(response.limitations().contains("billing-domain: No controllers found."));

        var endpoint = response.endpoints().get(0);
        assertEquals("billing-api:GET /api/billing/{id}", endpoint.endpointId());
        assertEquals("GET", endpoint.method());
        assertEquals("/api/billing/{id}", endpoint.path());
        assertEquals("Returns billing details.", endpoint.description());
        assertEquals("getBilling", endpoint.operationId());
        assertEquals("BillingController", endpoint.controllerClass());
        assertEquals("billing-api", endpoint.source().repositoryId());
        assertEquals("src/main/java/com/example/billing/BillingController.java", endpoint.source().filePath());
        assertEquals("id", endpoint.parameters().get(0).name());
        assertEquals("customer billing id", endpoint.tooltipDetails().parameters().get(0).description());

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

        var response = service.endpoints("billing-core", "feature/FLOW-42", null, null);

        assertEquals("feature/FLOW-42", response.requestedBranch());
        assertEquals("feature/FLOW-42", response.resolvedRef());

        var requestCaptor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService, times(2)).listEndpoints(requestCaptor.capture());
        assertTrue(requestCaptor.getAllValues().stream()
                .allMatch(request -> "feature/FLOW-42".equals(request.branch())));
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
                () -> service.endpoints("billing-core", null, null, null)
        );
    }

    private static FlowExplorerEndpointInventoryService service(
            GitLabRepositoryEndpointService endpointService,
            String gitLabGroup,
            String defaultBranch
    ) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup(gitLabGroup);
        var flowExplorerProperties = new FlowExplorerProperties();
        flowExplorerProperties.setDefaultBranch(defaultBranch);
        var scopeService = new FlowExplorerRepositoryScopeService(
                query -> catalog(),
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
                "GET /api/billing/{id}",
                List.of("GET"),
                "/api/billing/{id}",
                "/api/billing/{id}",
                "BillingController",
                "getBilling",
                "src/main/java/com/example/billing/BillingController.java",
                12,
                24,
                List.of("@PathVariable String id"),
                List.of("BillingResponse"),
                List.of("RestController", "GetMapping"),
                new GitLabRepositoryEndpointDocumentation(
                        "OPENAPI_YAML",
                        "Billing lookup",
                        "Returns billing details.",
                        "getBilling",
                        List.of("billing"),
                        List.of(new GitLabRepositoryEndpointParameterDocumentation(
                                "id",
                                "path",
                                true,
                                "string",
                                "customer billing id"
                        ))
                ),
                "high",
                List.of(),
                List.of("billing-api:src/main/java/com/example/billing/BillingController.java")
        );
    }

    private static OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "billing-core",
                        "name", "Billing Core",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("billing-api"))
                )),
                List.of(),
                List.of(
                        map(
                                "id", "billing-api",
                                "name", "Billing API",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "platform/backend",
                                        "projectPath", "platform/backend/billing-api"
                                )
                        ),
                        map(
                                "id", "billing-domain",
                                "name", "Billing Domain",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "platform/backend",
                                        "projectPath", "platform/backend/domains/billing-domain"
                                )
                        )
                ),
                List.of(map(
                        "id", "billing-scope",
                        "name", "Billing scope",
                        "target", map("type", "system", "id", "billing-core"),
                        "repositories", List.of(
                                map("repoId", "missing-repo", "priority", 1),
                                map("repoId", "billing-domain", "priority", 2)
                        )
                )),
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
