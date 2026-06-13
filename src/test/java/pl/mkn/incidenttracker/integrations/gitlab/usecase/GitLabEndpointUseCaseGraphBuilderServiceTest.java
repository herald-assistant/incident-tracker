package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseGraphBuilderServiceTest {

    private final GitLabEndpointUseCaseCodeIndexService codeIndexService = new GitLabEndpointUseCaseCodeIndexService();
    private final GitLabEndpointUseCaseEndpointIndexService endpointIndexService =
            new GitLabEndpointUseCaseEndpointIndexService();
    private final GitLabEndpointUseCaseSpringBeanRegistryService registryService =
            new GitLabEndpointUseCaseSpringBeanRegistryService();
    private final GitLabEndpointUseCaseDependencyInjectionResolver dependencyResolver =
            new GitLabEndpointUseCaseDependencyInjectionResolver();
    private final GitLabEndpointUseCaseCallTargetResolver callTargetResolver =
            new GitLabEndpointUseCaseCallTargetResolver();
    private final GitLabEndpointUseCaseGraphBuilderService graphBuilderService =
            new GitLabEndpointUseCaseGraphBuilderService();

    @Test
    void shouldBuildGraphFromControllerThroughUseCaseToRepositoryAndExternalClientTerminals() {
        var result = graph("""
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
                        validate(id);
                        repository.save(new Order());
                        paymentGateway.capture(id);
                    }

                    void validate(String id) {
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
                """, "POST", "/api/orders/123/submit", 8, 20);

        assertFalse(result.limits().maxDepthReached());
        assertFalse(result.limits().maxNodesReached());
        assertNode(result, "com.example.orders.OrderController#submit(String)",
                GitLabEndpointUseCaseRole.CONTROLLER, 0, false);
        assertNode(result, "com.example.orders.SubmitOrderUseCase#submit(String)",
                GitLabEndpointUseCaseRole.USE_CASE_SERVICE, 1, false);
        assertNode(result, "com.example.orders.SubmitOrderUseCase#validate(String)",
                GitLabEndpointUseCaseRole.USE_CASE_SERVICE, 2, false);
        assertNode(result, "com.example.orders.OrderRepository#save(Order)",
                GitLabEndpointUseCaseRole.REPOSITORY, 2, true);
        assertNode(result, "com.example.orders.PaymentGateway#capture(String)",
                GitLabEndpointUseCaseRole.EXTERNAL_CLIENT, 2, true);

        assertEdge(result, "useCase.submit(id)", GitLabEndpointUseCaseEdgeKind.SYNC_CALL,
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN);
        assertEdge(result, "validate(id)", GitLabEndpointUseCaseEdgeKind.VALIDATION,
                GitLabEndpointUseCaseResolutionKind.THIS_METHOD);
        assertEdge(result, "repository.save(new Order())", GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE,
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN);
        assertEdge(result, "paymentGateway.capture(id)", GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL,
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN);
    }

    @Test
    void shouldStopTraversalAtMaxDepthAndMaxNodes() {
        var source = """
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @Service
                class StepTwo {
                    void run() {
                    }
                }

                @Service
                @RequiredArgsConstructor
                class StepOneUseCase {
                    private final StepTwo stepTwo;

                    void start() {
                        stepTwo.run();
                    }
                }

                @RestController
                @RequestMapping("/api/orders")
                @RequiredArgsConstructor
                class OrderController {
                    private final StepOneUseCase stepOneUseCase;

                    @GetMapping("/{id}")
                    void get(String id) {
                        stepOneUseCase.start();
                    }
                }
                """;

        var depthLimited = graph(source, "GET", "/api/orders/42", 1, 20);

        assertTrue(depthLimited.limits().maxDepthReached());
        assertTrue(hasWarning(depthLimited, GitLabEndpointUseCaseWarningCodes.MAX_DEPTH_REACHED));
        assertNode(depthLimited, "com.example.orders.StepOneUseCase#start()",
                GitLabEndpointUseCaseRole.USE_CASE_SERVICE, 1, false);
        assertFalse(hasNode(depthLimited, "com.example.orders.StepTwo#run()"));

        var nodeLimited = graph(source, "GET", "/api/orders/42", 8, 1);

        assertTrue(nodeLimited.limits().maxNodesReached());
        assertTrue(hasWarning(nodeLimited, GitLabEndpointUseCaseWarningCodes.MAX_NODES_REACHED));
        assertEquals(1, nodeLimited.graph().nodes().size());
        assertEquals(List.of(), nodeLimited.graph().edges());
    }

    @Test
    void shouldAvoidExpandingCycles() {
        var result = graph("""
                package com.example.orders;

                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @Service
                class CycleUseCase {
                    void start() {
                        next();
                    }

                    void next() {
                        start();
                    }
                }

                @RestController
                @RequestMapping("/api/orders")
                @RequiredArgsConstructor
                class OrderController {
                    private final CycleUseCase cycleUseCase;

                    @GetMapping("/{id}")
                    void get(String id) {
                        cycleUseCase.start();
                    }
                }
                """, "GET", "/api/orders/42", 8, 20);

        assertTrue(hasWarning(result, GitLabEndpointUseCaseWarningCodes.CALL_GRAPH_CYCLE_DETECTED));
        assertEquals(3, result.graph().nodes().size());
        assertEdge(result, "start()", GitLabEndpointUseCaseEdgeKind.SYNC_CALL,
                GitLabEndpointUseCaseResolutionKind.THIS_METHOD);
    }

    private GitLabEndpointUseCaseGraphBuildResult graph(
            String source,
            String httpMethod,
            String endpointPath,
            int maxDepth,
            int maxNodes
    ) {
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
                        "src/main/java/com/example/orders/GraphFixture.java",
                        source,
                        source.length(),
                        false
                )),
                List.of()
        );
        var codeIndex = codeIndexService.buildIndex(snapshot);
        var endpointIndex = endpointIndexService.buildIndex(codeIndex);
        var endpoint = endpointIndexService.match(request(httpMethod, endpointPath), endpointIndex).endpoint();
        assertNotNull(endpoint);
        var registry = registryService.buildRegistry(codeIndex);
        var dependencyResolution = dependencyResolver.resolve(codeIndex, registry);
        var callTargetResolution = callTargetResolver.resolve(codeIndex, registry, dependencyResolution);
        return graphBuilderService.buildGraph(endpoint, codeIndex, registry, callTargetResolution, maxDepth, maxNodes);
    }

    private GitLabEndpointUseCaseContextRequest request(String httpMethod, String endpointPath) {
        return new GitLabEndpointUseCaseContextRequest(
                "orders-api",
                null,
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

    private static void assertNode(
            GitLabEndpointUseCaseGraphBuildResult result,
            String nodeId,
            GitLabEndpointUseCaseRole expectedRole,
            int expectedDepth,
            boolean expectedTerminal
    ) {
        var node = result.graph().nodes().stream()
                .filter(candidate -> nodeId.equals(candidate.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(node, "Expected node: " + nodeId);
        assertEquals(expectedRole, node.role());
        assertEquals(expectedDepth, node.depth());
        assertEquals(expectedTerminal, node.terminal());
    }

    private static boolean hasNode(GitLabEndpointUseCaseGraphBuildResult result, String nodeId) {
        return result.graph().nodes().stream().anyMatch(candidate -> nodeId.equals(candidate.id()));
    }

    private static void assertEdge(
            GitLabEndpointUseCaseGraphBuildResult result,
            String call,
            GitLabEndpointUseCaseEdgeKind expectedKind,
            GitLabEndpointUseCaseResolutionKind expectedResolutionKind
    ) {
        var edge = result.graph().edges().stream()
                .filter(candidate -> call.equals(candidate.call()))
                .findFirst()
                .orElse(null);
        assertNotNull(edge, "Expected edge for call: " + call);
        assertEquals(expectedKind, edge.kind());
        assertEquals(expectedResolutionKind, edge.resolutionKind());
    }

    private static boolean hasWarning(GitLabEndpointUseCaseGraphBuildResult result, String code) {
        return result.warnings().stream().anyMatch(warning -> code.equals(warning.code()));
    }
}
