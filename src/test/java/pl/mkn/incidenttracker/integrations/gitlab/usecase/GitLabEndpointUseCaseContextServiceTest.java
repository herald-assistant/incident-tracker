package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointUseCaseInput;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseContextServiceTest {

    @Test
    void shouldBuildEndpointUseCaseContextThroughFullPipeline() {
        var service = service(source());

        var result = service.buildContext(
                "tenant-alpha",
                "feature/FLOW-1",
                new GitLabEndpointUseCaseContextRequest(
                        "orders-api",
                        null,
                        "POST",
                        "/api/orders/123/submit",
                        null,
                        GitLabEndpointUseCaseOutputMode.COMPACT,
                        8,
                        80,
                        false,
                        "Opisuje endpoint submit order."
                )
        );

        assertEquals("tenant-alpha", result.repository().group());
        assertEquals("orders-api", result.repository().projectName());
        assertEquals("feature/FLOW-1", result.repository().requestedBranch());
        assertEquals(GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL, result.repository().indexStatus());
        assertNotNull(result.endpoint());
        assertEquals("/api/orders/123/submit", result.endpoint().inputPath());
        assertEquals("/api/orders/{id}/submit", result.endpoint().matchedPathPattern());
        assertEquals("com.example.orders.OrderController", result.endpoint().controllerClass());
        assertEquals("submit", result.endpoint().controllerMethod());
        assertEquals("submit order", result.useCaseSummary().mainResponsibility());
        assertTrue(result.useCaseSummary().sideEffects().contains("repository-write"));
        assertTrue(result.useCaseSummary().externalSystems().contains("Payment"));
        assertTrue(result.classList().stream()
                .anyMatch(item -> "com.example.orders.SubmitOrderUseCase".equals(item.classFqn())
                        && item.role() == GitLabEndpointUseCaseRole.USE_CASE_SERVICE));
        assertTrue(result.classList().stream()
                .anyMatch(item -> "com.example.orders.OrderRepository".equals(item.classFqn())
                        && item.terminal()));
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.confidence());
        assertFalse(result.graph().nodes().isEmpty());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.BRANCH_REF_NOT_IMMUTABLE.equals(warning.code())));
    }

    @Test
    void shouldReturnWarningsAndEmptyGraphWhenEndpointDoesNotMatch() {
        var service = service(source());

        var result = service.buildContext(
                "tenant-alpha",
                "feature/FLOW-1",
                new GitLabEndpointUseCaseContextRequest(
                        "orders-api",
                        null,
                        "DELETE",
                        "/api/orders/123",
                        null,
                        GitLabEndpointUseCaseOutputMode.COMPACT,
                        8,
                        80,
                        false,
                        "Sprawdzam brak endpointu."
                )
        );

        assertEquals("orders-api", result.repository().projectName());
        assertEquals(GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL, result.repository().indexStatus());
        assertEquals(null, result.endpoint());
        assertEquals(List.of(), result.graph().nodes());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, result.confidence());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.ENDPOINT_NOT_FOUND.equals(warning.code())));
    }

    @Test
    void shouldBuildEndpointUseCaseContextFromOpenApiBackedEndpointInventoryFallback() {
        var repositoryEndpointService = mock(GitLabRepositoryEndpointService.class);
        when(repositoryEndpointService.listEndpoints(any(GitLabRepositoryEndpointListRequest.class)))
                .thenReturn(new GitLabRepositoryEndpointListResult(
                        "tenant-alpha",
                        "orders-api",
                        "feature/FLOW-1",
                        null,
                        "GET",
                        "src/main/java",
                        1,
                        1,
                        false,
                        List.of(openApiBackedEndpoint()),
                        List.of()
                ));
        var service = service(openApiBackedSource(), repositoryEndpointService);

        var result = service.buildContext(
                "tenant-alpha",
                "feature/FLOW-1",
                new GitLabEndpointUseCaseContextRequest(
                        "orders-api",
                        null,
                        "GET",
                        "/api/crm/customers/123",
                        null,
                        GitLabEndpointUseCaseOutputMode.COMPACT,
                        8,
                        80,
                        false,
                        "Opisuje endpoint customer profile."
                )
        );

        assertNotNull(result.endpoint());
        assertEquals("/api/crm/customers/123", result.endpoint().inputPath());
        assertEquals("/api/crm/customers/{customerId}", result.endpoint().matchedPathPattern());
        assertEquals("com.example.crm.customer.CustomerDataController", result.endpoint().controllerClass());
        assertEquals("getCustomer", result.endpoint().controllerMethod());
        assertTrue(result.classList().stream()
                .anyMatch(item -> "com.example.crm.customer.CustomerLookupUseCase".equals(item.classFqn())
                        && item.role() == GitLabEndpointUseCaseRole.USE_CASE_SERVICE));
        assertFalse(result.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.ENDPOINT_NOT_FOUND.equals(warning.code())));
    }

    private GitLabEndpointUseCaseContextService service(String source) {
        var repositoryEndpointService = mock(GitLabRepositoryEndpointService.class);
        when(repositoryEndpointService.listEndpoints(any(GitLabRepositoryEndpointListRequest.class)))
                .thenReturn(new GitLabRepositoryEndpointListResult(
                        "tenant-alpha",
                        "orders-api",
                        "feature/FLOW-1",
                        null,
                        null,
                        "src/main/java",
                        0,
                        0,
                        false,
                        List.of(),
                        List.of()
                ));
        return service(source, repositoryEndpointService);
    }

    private GitLabEndpointUseCaseContextService service(
            String source,
            GitLabRepositoryEndpointService repositoryEndpointService
    ) {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        var treeService = mock(GitLabRepositoryTreeService.class);
        when(treeService.requestScopedSession(any())).thenReturn(null);
        when(treeService.fetchRepositoryBlobs(
                eq("https://gitlab.example.com"),
                eq("tenant-alpha/orders-api"),
                eq("feature/FLOW-1"),
                eq("src/main/java"),
                any()
        )).thenReturn(List.of(new GitLabRepositoryTreeNode(
                "src/main/java/com/example/orders/OrderFlow.java",
                "blob"
        )));

        var repositoryPort = mock(GitLabRepositoryPort.class);
        when(repositoryPort.readFile(
                eq("tenant-alpha"),
                eq("orders-api"),
                eq("feature/FLOW-1"),
                eq("src/main/java/com/example/orders/OrderFlow.java"),
                eq(80_000)
        )).thenReturn(new GitLabRepositoryFileContent(
                "tenant-alpha",
                "orders-api",
                "feature/FLOW-1",
                "src/main/java/com/example/orders/OrderFlow.java",
                source,
                false
        ));

        return new GitLabEndpointUseCaseContextService(
                new GitLabEndpointUseCaseSourceSnapshotService(properties, treeService, repositoryPort, 500, 80_000),
                new GitLabEndpointUseCaseCodeIndexService(),
                new GitLabEndpointUseCaseEndpointIndexService(),
                new GitLabEndpointUseCaseSpringBeanRegistryService(),
                new GitLabEndpointUseCaseDependencyInjectionResolver(),
                new GitLabEndpointUseCaseCallTargetResolver(),
                new GitLabEndpointUseCaseGraphBuilderService(),
                new GitLabEndpointUseCaseContextCompressorService(),
                repositoryEndpointService
        );
    }

    private GitLabRepositoryEndpoint openApiBackedEndpoint() {
        return new GitLabRepositoryEndpoint(
                "GET /api/crm/customers/{customerId} -> com.example.crm.customer.CustomerDataController#getCustomer",
                List.of("GET"),
                "/api/crm/customers/{customerId}",
                null,
                "com.example.crm.customer.CustomerDataController",
                "getCustomer",
                "src/main/java/com/example/orders/OrderFlow.java",
                23,
                26,
                List.of("String"),
                List.of("CustomerDto"),
                List.of("RestController", "OpenApiContract", "Implements CustomerDataApi", "OperationId getCustomer"),
                "high",
                List.of("Endpoint mapping resolved from OpenAPI YAML contract, not Java annotations."),
                new GitLabRepositoryEndpointUseCaseInput(
                        "orders-api",
                        "GET /api/crm/customers/{customerId} -> com.example.crm.customer.CustomerDataController#getCustomer",
                        List.of("GET"),
                        "/api/crm/customers/{customerId}",
                        "src/main/java/com/example/orders/OrderFlow.java",
                        23,
                        26
                ),
                List.of(
                        "src/main/resources/openapi/customer-api.yaml:12",
                        "src/main/java/com/example/orders/OrderFlow.java:23"
                )
        );
    }

    private String source() {
        return """
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                class Order {
                }

                @FeignClient(name = "paymentGateway")
                interface PaymentGateway {
                    void capture(String id);
                }

                interface OrderRepository extends JpaRepository<Order, String> {
                    void save(Order order);
                }

                @Service
                @RequiredArgsConstructor
                class SubmitOrderUseCase {
                    private final OrderRepository repository;
                    private final PaymentGateway paymentGateway;

                    void submit(String id) {
                        repository.save(new Order());
                        paymentGateway.capture(id);
                    }
                }

                @RestController
                @RequestMapping("/api/orders")
                @RequiredArgsConstructor
                class OrderController {
                    private final SubmitOrderUseCase useCase;

                    @PostMapping("/{id}/submit")
                    void submit(@PathVariable String id) {
                        useCase.submit(id);
                    }
                }
                """;
    }

    private String openApiBackedSource() {
        return """
                package com.example.crm.customer;

                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.RestController;

                class CustomerDto {
                }

                interface CustomerDataApi {
                    CustomerDto getCustomer(String customerId);
                }

                @Service
                class CustomerLookupUseCase {
                    CustomerDto load(String customerId) {
                        return new CustomerDto();
                    }
                }

                @RestController
                @RequiredArgsConstructor
                class CustomerDataController implements CustomerDataApi {
                    private final CustomerLookupUseCase useCase;

                    public CustomerDto getCustomer(String customerId) {
                        return useCase.load(customerId);
                    }
                }
                """;
    }
}
