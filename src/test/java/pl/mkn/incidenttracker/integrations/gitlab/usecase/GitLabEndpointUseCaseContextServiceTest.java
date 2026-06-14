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
                "GET /api/products/{id} -> com.example.crm.ProductController#getProduct",
                List.of("GET"),
                "/api/products/{id}",
                "getProduct"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint)));
        stubControllerSource("CRM", "crm-product-service", "release/1", "src/main/java", endpoint.filePath());

        var result = service.buildContext("CRM", "release/1", new GitLabEndpointUseCaseContextRequest(
                "crm-product-service",
                endpoint.endpointId(),
                null,
                null,
                "src/main/java",
                4,
                10,
                "manual verification"
        ));

        assertEquals("CRM", result.repository().group());
        assertEquals("crm-product-service", result.repository().projectName());
        assertEquals("release/1", result.repository().branch());
        assertEquals(endpoint.endpointId(), result.endpoint().endpointId());
        assertEquals(GitLabEndpointUseCaseFileRole.CONTROLLER, result.files().get(0).role());
        assertEquals("src/main/java/com/example/crm/ProductController.java", result.files().get(0).path());
        assertTrue(result.files().get(0).symbols().contains("getProduct"));
        assertFalse(result.limits().maxFilesReached());

        var endpointRequest = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(endpointRequest.capture());
        assertEquals("CRM", endpointRequest.getValue().group());
        assertEquals("crm-product-service", endpointRequest.getValue().projectName());
        assertEquals("release/1", endpointRequest.getValue().branch());
        assertNull(endpointRequest.getValue().endpointPathPrefix());
        assertNull(endpointRequest.getValue().httpMethod());
        verify(repositoryPort).readFile(
                "CRM",
                "crm-product-service",
                "release/1",
                "src/main/java/com/example/crm/ProductController.java",
                120_000
        );
    }

    @Test
    void shouldBuildContextForHttpMethodAndPath() {
        var endpoint = endpoint(
                "PUT /api/products/{id} -> com.example.crm.ProductController#updateProduct",
                List.of("PUT"),
                "/api/products/{id}",
                "updateProduct"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint)));
        stubControllerSource("CRM", "crm-product-service", "main", "src/main/java", endpoint.filePath());

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-product-service",
                null,
                "put",
                "/api/products/{id}",
                null,
                4,
                10,
                null
        ));

        assertEquals("updateProduct", result.endpoint().handlerMethod());
        assertTrue(result.files().stream()
                .anyMatch(file -> file.path().equals("src/main/java/com/example/crm/ProductController.java")
                        && file.symbols().contains("updateProduct")));

        var endpointRequest = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(endpointRequest.capture());
        assertEquals("/api/products/{id}", endpointRequest.getValue().endpointPathPrefix());
        assertEquals("PUT", endpointRequest.getValue().httpMethod());
        assertEquals("src/main/java", endpointRequest.getValue().sourcePathPrefix());
    }

    @Test
    void shouldReturnCompactResultWhenEndpointNotFound() {
        var candidate = endpoint(
                "GET /api/products/{id}/history -> com.example.crm.ProductController#getHistory",
                List.of("GET"),
                "/api/products/{id}/history",
                "getHistory"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(candidate)));

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-product-service",
                null,
                "GET",
                "/api/products/{id}",
                null,
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
                .contains("crm-product-service:src/main/java/com/example/crm/ProductController.java"));
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldReturnCompactResultWhenEndpointIsAmbiguous() {
        var first = endpoint(
                "GET /api/products/{id} -> com.example.crm.ProductController#getProduct",
                List.of("GET"),
                "/api/products/{id}",
                "getProduct"
        );
        var second = new GitLabRepositoryEndpoint(
                "GET /api/products/{id} -> com.example.crm.LegacyProductController#getProduct",
                List.of("GET"),
                "/api/products/{id}",
                "/api/products/{id}",
                "com.example.crm.LegacyProductController",
                "getProduct",
                "src/main/java/com/example/crm/LegacyProductController.java",
                1,
                5,
                List.of(),
                List.of("ProductWebModel"),
                List.of(),
                "medium",
                List.of(),
                List.of()
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(first, second)));

        var result = service.buildContext("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-product-service",
                null,
                "GET",
                "/api/products/{id}",
                null,
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
                "crm-product-service",
                null,
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
            String sourcePathPrefix,
            String controllerPath
    ) {
        when(repositoryPort.listRepositoryFiles(group, project, branch, sourcePathPrefix))
                .thenReturn(List.of(new GitLabRepositoryFile(group, project, branch, controllerPath)));
        when(repositoryPort.readFile(group, project, branch, controllerPath, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(group, project, branch, controllerPath, """
                        package com.example.crm;

                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        class ProductController {
                            ProductWebModel getProduct(String id) {
                                return new ProductWebModel(id);
                            }

                            ProductWebModel updateProduct(String id) {
                                return new ProductWebModel(id);
                            }
                        }

                        record ProductWebModel(String id) {
                        }
                        """, false));
    }

    private GitLabRepositoryEndpointListResult endpointList(List<GitLabRepositoryEndpoint> endpoints) {
        return new GitLabRepositoryEndpointListResult(
                "CRM",
                "crm-product-service",
                "main",
                null,
                null,
                "src/main/java",
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
                "com.example.crm.ProductController",
                handlerMethod,
                "src/main/java/com/example/crm/ProductController.java",
                1,
                5,
                List.of("ProductWebModel"),
                List.of("ProductWebModel"),
                List.of("RestController"),
                "high",
                List.of(),
                List.of()
        );
    }
}
