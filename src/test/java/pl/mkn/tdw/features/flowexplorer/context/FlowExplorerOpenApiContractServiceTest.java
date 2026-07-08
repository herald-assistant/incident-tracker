package pl.mkn.tdw.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerOpenApiContractServiceTest {

    private final GitLabOpenApiEndpointSliceService openApiEndpointSliceService =
            mock(GitLabOpenApiEndpointSliceService.class);
    private final FlowExplorerOpenApiContractService service =
            new FlowExplorerOpenApiContractService(openApiEndpointSliceService);

    @Test
    void shouldReadOpenApiContractWithinRepositoryBoundary() {
        when(openApiEndpointSliceService.readEndpointSlice(any()))
                .thenAnswer(invocation -> response(invocation.getArgument(0)));

        var result = service.buildEndpointContracts(
                "platform/backend",
                "main",
                repository(List.of("src/main/resources")),
                List.of(node("src/main/resources/openapi/customer-profile.yaml")),
                new FlowExplorerContextRequest(
                        "crm-customer-profile",
                        null,
                        "GET",
                        "/api/customers/{customerId}/profile",
                        "main",
                        null,
                        List.of()
                )
        );

        assertEquals(1, result.contracts().size());
        assertEquals("src/main/resources/openapi/customer-profile.yaml",
                result.contracts().get(0).filePath());
        verify(openApiEndpointSliceService).readEndpointSlice(any());
    }

    @Test
    void shouldReadExplicitOpenApiContractOutsideRepositoryBoundaryWithVisibilityLimitation() {
        when(openApiEndpointSliceService.readEndpointSlice(any()))
                .thenAnswer(invocation -> response(invocation.getArgument(0)));

        var result = service.buildEndpointContracts(
                "platform/backend",
                "main",
                repository(List.of("src/main/java/com/example/crm/customerprofile")),
                List.of(node("src/main/resources/openapi/customer-profile.yaml")),
                new FlowExplorerContextRequest(
                        "crm-customer-profile",
                        null,
                        "GET",
                        "/api/customers/{customerId}/profile",
                        "main",
                        null,
                        List.of()
                )
        );

        assertEquals(1, result.contracts().size());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("outside default repository discovery scope")));
        assertTrue(result.contracts().get(0).limitations().stream()
                .anyMatch(limitation -> limitation.contains("outside default repository discovery scope")));
        verify(openApiEndpointSliceService).readEndpointSlice(any());
    }

    private static FlowExplorerRepositoryContext repository(List<String> pathPrefixes) {
        return new FlowExplorerRepositoryContext(
                "crm-service",
                "crm-service",
                "platform/backend/crm-service",
                "main",
                pathPrefixes.isEmpty() ? "whole-repository" : "path-prefixes",
                pathPrefixes,
                true,
                true,
                List.of()
        );
    }

    private static FlowExplorerFlowNode node(String filePath) {
        return new FlowExplorerFlowNode(
                filePath,
                "OPENAPI_CONTRACT",
                filePath,
                List.of(),
                "OpenAPI contract discovered by endpoint inventory.",
                "HIGH",
                List.of()
        );
    }

    private static GitLabOpenApiEndpointSliceResponse response(GitLabOpenApiEndpointSliceRequest request) {
        return new GitLabOpenApiEndpointSliceResponse(
                request.group(),
                request.projectName(),
                request.branch(),
                request.filePath(),
                GitLabOpenApiEndpointSliceService.STATUS_OK,
                "openapi",
                "3.0.3",
                request.httpMethod(),
                request.endpointPath(),
                "/api/customers/{customerId}/profile",
                "getCustomerProfile",
                "Get customer profile",
                null,
                List.of("customer"),
                request.filePath() + "#/paths/~1api~1customers~1{customerId}~1profile/get",
                "GET /api/customers/{customerId}/profile",
                40,
                false,
                List.of()
        );
    }
}
