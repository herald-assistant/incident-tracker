package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseContextServiceTest {

    private final GitLabRepositoryEndpointService endpointService = mock(GitLabRepositoryEndpointService.class);
    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseEndpointResolver endpointResolver = new GitLabEndpointUseCaseEndpointResolver(endpointService);
    private final GitLabEndpointUseCaseContextService service = new GitLabEndpointUseCaseContextService(
            endpointResolver,
            repositoryPort,
            new GitLabEndpointUseCaseTraversalService()
    );

    @Test
    void shouldBuildContextForEndpointId() {
        var endpoint = endpoint(
                "GET /api/customers/{id} -> com.example.crm.CustomerController#getCustomer",
                List.of("GET"),
                "/api/customers/{id}",
                "getCustomer"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint)));
        stubControllerSource("CRM", "crm-customer-service", "release/1", endpoint.filePath());

        var result = service.buildContext("CRM", "release/1", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                endpoint.endpointId(),
                null,
                null,
                4,
                10,
                "manual verification"
        ));

        assertEquals("CRM", result.repository().group());
        assertEquals("crm-customer-service", result.repository().projectName());
        assertEquals("release/1", result.repository().branch());
        assertEquals(endpoint.endpointId(), result.endpoint().endpointId());
        assertEquals(GitLabEndpointUseCaseFileRole.CONTROLLER, result.files().get(0).role());
        assertEquals("src/main/java/com/example/crm/CustomerController.java", result.files().get(0).path());
        assertTrue(result.files().get(0).symbols().contains("getCustomer"));
        assertFalse(result.limits().maxFilesReached());

        var endpointRequest = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(endpointRequest.capture());
        assertEquals("CRM", endpointRequest.getValue().group());
        assertEquals("crm-customer-service", endpointRequest.getValue().projectName());
        assertEquals("release/1", endpointRequest.getValue().branch());
        assertNull(endpointRequest.getValue().endpointPathPrefix());
        assertNull(endpointRequest.getValue().httpMethod());
        verify(repositoryPort).readFile(
                "CRM",
                "crm-customer-service",
                "release/1",
                "src/main/java/com/example/crm/CustomerController.java",
                120_000
        );
    }

    @Test
    void shouldBuildContextForHttpMethodAndPath() {
        var endpoint = endpoint(
                "PUT /api/customers/{id} -> com.example.crm.CustomerController#updateCustomer",
                List.of("PUT"),
                "/api/customers/{id}",
                "updateCustomer"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint)));
        stubControllerSource("CRM", "crm-customer-service", "main", endpoint.filePath());

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "put",
                "/api/customers/{id}",
                4,
                10,
                null
        ));

        assertEquals("updateCustomer", result.endpoint().handlerMethod());
        assertTrue(result.files().stream()
                .anyMatch(file -> file.path().equals("src/main/java/com/example/crm/CustomerController.java")
                        && file.symbols().contains("updateCustomer")));

        var endpointRequest = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(endpointRequest.capture());
        assertEquals("/api/customers/{id}", endpointRequest.getValue().endpointPathPrefix());
        assertEquals("PUT", endpointRequest.getValue().httpMethod());
    }

    @Test
    void shouldReturnCompactResultWhenEndpointNotFound() {
        var candidate = endpoint(
                "GET /api/customers/{id}/history -> com.example.crm.CustomerController#getHistory",
                List.of("GET"),
                "/api/customers/{id}/history",
                "getHistory"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(candidate)));

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "GET",
                "/api/customers/{id}",
                null,
                null,
                null
        ));

        assertNull(result.endpoint());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, result.confidence());
        assertEquals(1, result.files().size());
        assertEquals("getHistory", result.files().get(0).symbols().get(0));
        assertEquals(1, result.unresolved().size());
        assertTrue(result.unresolved().get(0).reason().contains("ENDPOINT_NOT_FOUND"));
        assertTrue(result.suggestedNextReads().get(0)
                .contains("crm-customer-service:src/main/java/com/example/crm/CustomerController.java"));
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldReturnCompactResultWhenEndpointIsAmbiguous() {
        var first = endpoint(
                "GET /api/customers/{id} -> com.example.crm.CustomerController#getCustomer",
                List.of("GET"),
                "/api/customers/{id}",
                "getCustomer"
        );
        var second = new GitLabRepositoryEndpoint(
                "GET /api/customers/{id} -> com.example.crm.LegacyCustomerController#getCustomer",
                List.of("GET"),
                "/api/customers/{id}",
                "/api/customers/{id}",
                "com.example.crm.LegacyCustomerController",
                "getCustomer",
                "src/main/java/com/example/crm/LegacyCustomerController.java",
                1,
                5,
                List.of(),
                List.of("CustomerModel"),
                List.of(),
                "medium",
                List.of(),
                List.of()
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(first, second)));

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "GET",
                "/api/customers/{id}",
                null,
                null,
                null
        ));

        assertNull(result.endpoint());
        assertEquals(2, result.files().size());
        assertTrue(result.unresolved().get(0).reason().contains("AMBIGUOUS_ENDPOINT"));
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("choose endpointId")));
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldReturnInvalidRequestWithoutCallingGitLab() {
        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertNull(result.endpoint());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, result.confidence());
        assertTrue(result.limitations().contains("endpointId or httpMethod + endpointPath is required."));
        verifyNoInteractions(endpointService, repositoryPort);
    }

    private void stubControllerSource(
            String group,
            String project,
            String branch,
            String controllerPath
    ) {
        when(repositoryPort.listRepositoryFiles(group, project, branch, null))
                .thenReturn(List.of(new GitLabRepositoryFile(group, project, branch, controllerPath)));
        when(repositoryPort.readFile(group, project, branch, controllerPath, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(group, project, branch, controllerPath, """
                        package com.example.crm;

                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        class CustomerController {
                            CustomerModel getCustomer(String id) {
                                return new CustomerModel(id);
                            }

                            CustomerModel updateCustomer(String id) {
                                return new CustomerModel(id);
                            }
                        }

                        record CustomerModel(String id) {
                        }
                        """, false));
    }

    private GitLabRepositoryEndpointListResult endpointList(List<GitLabRepositoryEndpoint> endpoints) {
        return new GitLabRepositoryEndpointListResult(
                "CRM",
                "crm-customer-service",
                "main",
                null,
                null,
                endpoints.size(),
                endpoints.size(),
                false,
                endpoints,
                List.of()
        );
    }

    private GitLabRepositoryEndpoint endpoint(
            String endpointId,
            List<String> httpMethods,
            String path,
            String handlerMethod
    ) {
        return new GitLabRepositoryEndpoint(
                endpointId,
                httpMethods,
                path,
                path,
                "com.example.crm.CustomerController",
                handlerMethod,
                "src/main/java/com/example/crm/CustomerController.java",
                1,
                5,
                List.of("CustomerModel"),
                List.of("CustomerModel"),
                List.of("RestController"),
                "high",
                List.of(),
                List.of()
        );
    }
}
