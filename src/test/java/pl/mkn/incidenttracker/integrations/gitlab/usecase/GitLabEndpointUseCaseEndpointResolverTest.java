package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseEndpointResolverTest {

    private final GitLabRepositoryEndpointService endpointService = mock(GitLabRepositoryEndpointService.class);
    private final GitLabEndpointUseCaseEndpointResolver resolver = new GitLabEndpointUseCaseEndpointResolver(endpointService);

    @Test
    void shouldResolveEndpointByEndpointId() {
        var endpoint = endpoint(
                "GET /api/crm/customers/{id} -> com.example.crm.CustomerController#getCustomer",
                List.of("GET"),
                "/api/crm/customers/{id}",
                "com.example.crm.CustomerController",
                "getCustomer",
                "high"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint), List.of("crm limitation")));

        var resolution = resolver.resolve("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                endpoint.endpointId(),
                null,
                null,
                null,
                null
        ));

        assertEquals(GitLabEndpointUseCaseEndpointResolutionStatus.RESOLVED, resolution.status());
        assertEquals("CRM", resolution.repository().group());
        assertEquals("crm-customer-service", resolution.repository().projectName());
        assertEquals("main", resolution.repository().branch());
        assertEquals(endpoint.endpointId(), resolution.endpoint().endpointId());
        assertEquals("getCustomer", resolution.endpoint().handlerMethod());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.endpoint().confidence());
        assertEquals(List.of("crm limitation", "endpoint limitation"), resolution.limitations());
        assertEquals(List.of("OpenApiContract"), resolution.endpoint().annotations());

        var captor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(captor.capture());
        assertNull(captor.getValue().endpointPathPrefix());
        assertNull(captor.getValue().httpMethod());
    }

    @Test
    void shouldResolveEndpointByHttpMethodAndPath() {
        var endpoint = endpoint(
                "PUT /api/crm/customers/{id} -> com.example.crm.CustomerController#updateCustomer",
                List.of("PUT"),
                "/api/crm/customers/{id}/",
                "com.example.crm.CustomerController",
                "updateCustomer",
                "medium"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(endpoint), List.of()));

        var resolution = resolver.resolve("CRM", "develop", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "put",
                "/api/crm/customers/{id}",
                null,
                null
        ));

        assertEquals(GitLabEndpointUseCaseEndpointResolutionStatus.RESOLVED, resolution.status());
        assertEquals("updateCustomer", resolution.endpoint().handlerMethod());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, resolution.endpoint().confidence());

        var captor = ArgumentCaptor.forClass(GitLabRepositoryEndpointListRequest.class);
        verify(endpointService).listEndpoints(captor.capture());
        assertEquals("/api/crm/customers/{id}", captor.getValue().endpointPathPrefix());
        assertEquals("PUT", captor.getValue().httpMethod());
    }

    @Test
    void shouldReturnAmbiguousEndpointWhenPathMatchesManyHandlers() {
        var first = endpoint(
                "GET /api/crm/customers/{id} -> com.example.crm.CustomerController#getCustomer",
                List.of("GET"),
                "/api/crm/customers/{id}",
                "com.example.crm.CustomerController",
                "getCustomer",
                "high"
        );
        var second = endpoint(
                "GET /api/crm/customers/{id} -> com.example.crm.LegacyCustomerController#getCustomer",
                List.of("GET"),
                "/api/crm/customers/{id}",
                "com.example.crm.LegacyCustomerController",
                "getCustomer",
                "high"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(first, second), List.of()));

        var resolution = resolver.resolve("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "GET",
                "/api/crm/customers/{id}",
                null,
                null
        ));

        assertEquals(GitLabEndpointUseCaseEndpointResolutionStatus.AMBIGUOUS_ENDPOINT, resolution.status());
        assertNull(resolution.endpoint());
        assertEquals(2, resolution.candidates().size());
        assertEquals("com.example.crm.CustomerController", resolution.candidates().get(0).controllerClass());
        assertEquals("com.example.crm.LegacyCustomerController", resolution.candidates().get(1).controllerClass());
        assertEquals(List.of("More than one endpoint matched the requested selector; choose endpointId for an exact start point."),
                resolution.limitations());
    }

    @Test
    void shouldReturnEndpointNotFoundWithCandidates() {
        var candidate = endpoint(
                "GET /api/crm/customers/{id}/history -> com.example.crm.CustomerController#getHistory",
                List.of("GET"),
                "/api/crm/customers/{id}/history",
                "com.example.crm.CustomerController",
                "getHistory",
                "high"
        );
        when(endpointService.listEndpoints(any())).thenReturn(endpointList(List.of(candidate), List.of()));

        var resolution = resolver.resolve("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                "GET",
                "/api/crm/customers/{id}",
                null,
                null
        ));

        assertEquals(GitLabEndpointUseCaseEndpointResolutionStatus.ENDPOINT_NOT_FOUND, resolution.status());
        assertNull(resolution.endpoint());
        assertEquals(1, resolution.candidates().size());
        assertEquals("getHistory", resolution.candidates().get(0).handlerMethod());
        assertEquals(List.of("Endpoint was not found by httpMethod + endpointPath: GET /api/crm/customers/{id}"),
                resolution.limitations());
    }

    @Test
    void shouldReturnInvalidRequestWhenSelectorIsMissing() {
        var resolution = resolver.resolve("CRM", "main", new GitLabEndpointUseCaseContextRequest(
                "crm-customer-service",
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(GitLabEndpointUseCaseEndpointResolutionStatus.INVALID_REQUEST, resolution.status());
        assertEquals(List.of("endpointId or httpMethod + endpointPath is required."), resolution.limitations());
    }

    private GitLabRepositoryEndpointListResult endpointList(
            List<GitLabRepositoryEndpoint> endpoints,
            List<String> limitations
    ) {
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
                limitations
        );
    }

    private GitLabRepositoryEndpoint endpoint(
            String endpointId,
            List<String> httpMethods,
            String path,
            String controllerClass,
            String handlerMethod,
            String confidence
    ) {
        return new GitLabRepositoryEndpoint(
                endpointId,
                httpMethods,
                path,
                path,
                controllerClass,
                handlerMethod,
                "src/main/java/com/example/crm/CustomerController.java",
                10,
                15,
                List.of("CustomerRequest"),
                List.of("CustomerResponse"),
                List.of("OpenApiContract"),
                confidence,
                List.of("endpoint limitation"),
                List.of("crm-customer-service:src/main/java/com/example/crm/CustomerController.java lines 10-15 via gitlab_read_repository_file_chunk")
        );
    }
}
