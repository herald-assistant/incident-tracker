package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseEndpointIndexServiceTest {

    private final GitLabEndpointUseCaseCodeIndexService codeIndexService = new GitLabEndpointUseCaseCodeIndexService();
    private final GitLabEndpointUseCaseEndpointIndexService endpointIndexService =
            new GitLabEndpointUseCaseEndpointIndexService();

    @Test
    void shouldMatchEndpointByHttpMethodAndPathVariableWithTrailingSlash() {
        var endpointIndex = endpointIndex("""
                package com.example.orders.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {

                    @GetMapping(path = "/{orderId}")
                    OrderResponse getOrder(@PathVariable String orderId) {
                        return null;
                    }
                }
                """);

        var result = endpointIndexService.match(request(null, "GET", "/api/orders/123/"), endpointIndex);

        assertTrue(result.matched());
        assertEquals("com.example.orders.api.OrderController", result.endpoint().controllerClass());
        assertEquals("getOrder", result.endpoint().controllerMethod());
        assertEquals("/api/orders/{orderId}", result.endpoint().pathPattern());
        assertEquals(List.of("GET"), result.endpoint().httpMethods());
    }

    @Test
    void shouldMatchEndpointByEndpointId() {
        var endpointIndex = endpointIndex("""
                package com.example.orders.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {

                    @GetMapping("/{orderId}")
                    OrderResponse getOrder(String orderId) {
                        return null;
                    }
                }
                """);
        var endpointId = "GET /api/orders/{orderId} -> com.example.orders.api.OrderController#getOrder";

        var result = endpointIndexService.match(request(endpointId, null, null), endpointIndex);

        assertTrue(result.matched());
        assertEquals(endpointId, result.endpoint().endpointId());
    }

    @Test
    void shouldSupportMultipleMethodMappingsAndRequestMappingMethodAttribute() {
        var endpointIndex = endpointIndex("""
                package com.example.orders.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(path = "/api/orders")
                class OrderController {

                    @GetMapping(path = {"/active", "/enabled"})
                    List<OrderResponse> activeOrders() {
                        return List.of();
                    }

                    @RequestMapping(value = "/search", method = RequestMethod.POST)
                    List<OrderResponse> searchOrders() {
                        return List.of();
                    }
                }
                """);

        var enabledResult = endpointIndexService.match(request(null, "GET", "/api/orders/enabled"), endpointIndex);
        var searchResult = endpointIndexService.match(request(null, "POST", "/api/orders/search"), endpointIndex);

        assertTrue(enabledResult.matched());
        assertEquals("/api/orders/enabled", enabledResult.endpoint().pathPattern());
        assertTrue(searchResult.matched());
        assertEquals("searchOrders", searchResult.endpoint().controllerMethod());
        assertEquals(List.of("POST"), searchResult.endpoint().httpMethods());
    }

    @Test
    void shouldReportAmbiguousEndpointMatch() {
        var endpointIndex = endpointIndex("""
                package com.example.orders.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {

                    @GetMapping("/{orderId}")
                    OrderResponse getOrder(String orderId) {
                        return null;
                    }

                    @GetMapping("/{externalId}")
                    OrderResponse getOrderByExternalId(String externalId) {
                        return null;
                    }
                }
                """);

        var result = endpointIndexService.match(request(null, "GET", "/api/orders/123"), endpointIndex);

        assertFalse(result.matched());
        assertTrue(result.ambiguous());
        assertEquals(2, result.candidates().size());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.ENDPOINT_AMBIGUOUS.equals(warning.code())));
    }

    @Test
    void shouldReportNotFoundWithAvailableCandidates() {
        var endpointIndex = endpointIndex("""
                package com.example.orders.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/orders")
                class OrderController {

                    @GetMapping("/{orderId}")
                    OrderResponse getOrder(String orderId) {
                        return null;
                    }
                }
                """);

        var result = endpointIndexService.match(request(null, "POST", "/api/orders/123"), endpointIndex);

        assertFalse(result.matched());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.ENDPOINT_NOT_FOUND.equals(warning.code())
                        && !warning.candidates().isEmpty()));
    }

    private GitLabEndpointUseCaseEndpointIndex endpointIndex(String source) {
        var snapshot = new GitLabEndpointUseCaseSourceSnapshot(
                "tenant-alpha",
                "orders-api",
                "main",
                "src/main/java",
                GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL,
                1,
                1,
                500,
                80_000,
                false,
                false,
                List.of(new GitLabEndpointUseCaseSourceFile(
                        "src/main/java/com/example/orders/api/OrderController.java",
                        source,
                        source.length(),
                        false
                )),
                List.of()
        );
        return endpointIndexService.buildIndex(codeIndexService.buildIndex(snapshot));
    }

    private GitLabEndpointUseCaseContextRequest request(String endpointId, String httpMethod, String endpointPath) {
        return new GitLabEndpointUseCaseContextRequest(
                "orders-api",
                endpointId,
                httpMethod,
                endpointPath,
                null,
                null,
                null,
                null,
                null,
                "test"
        );
    }
}
