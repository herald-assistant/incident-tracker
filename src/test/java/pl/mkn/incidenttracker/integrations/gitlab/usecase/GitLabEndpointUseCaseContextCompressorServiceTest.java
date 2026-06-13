package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseContextCompressorServiceTest {

    private final GitLabEndpointUseCaseContextCompressorService compressorService =
            new GitLabEndpointUseCaseContextCompressorService();

    @Test
    void shouldBuildCompactClassListSummaryEvidenceAndConfidence() {
        var compressed = compressorService.compress(endpoint(), graphBuildResult(graph(), List.of(), limits(false, false)),
                GitLabEndpointUseCaseOutputMode.COMPACT);

        assertEquals("submit order", compressed.useCaseSummary().mainResponsibility());
        assertTrue(compressed.useCaseSummary().businessObjects().contains("Order"));
        assertTrue(compressed.useCaseSummary().sideEffects().contains("repository-write"));
        assertTrue(compressed.useCaseSummary().sideEffects().contains("external-call"));
        assertEquals(List.of("Payment"), compressed.useCaseSummary().externalSystems());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, compressed.confidence());

        assertClassItem(compressed, "com.example.orders.OrderController", GitLabEndpointUseCaseRole.CONTROLLER);
        assertClassItem(compressed, "com.example.orders.SubmitOrderUseCase",
                GitLabEndpointUseCaseRole.USE_CASE_SERVICE);
        assertClassItem(compressed, "com.example.orders.OrderMapper", GitLabEndpointUseCaseRole.MAPPER);
        assertClassItem(compressed, "com.example.orders.OrderRepository", GitLabEndpointUseCaseRole.REPOSITORY);
        assertFalse(hasClassItem(compressed, "com.example.orders.TechnicalHelper"));

        assertTrue(hasEdge(compressed.graph(), GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE));
        assertTrue(hasEdge(compressed.graph(), GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL));
        assertTrue(compressed.evidence().stream().noneMatch(evidence -> evidence.message().contains("class Order")));
        assertFalse(compressed.suggestedNextReads().isEmpty());
    }

    @Test
    void shouldKeepFullGraphForGraphAndDebugModes() {
        var graph = graph();

        var graphMode = compressorService.compress(endpoint(), graphBuildResult(graph, List.of(), limits(false, false)),
                GitLabEndpointUseCaseOutputMode.GRAPH);
        var debugMode = compressorService.compress(endpoint(), graphBuildResult(graph, List.of(), limits(false, false)),
                GitLabEndpointUseCaseOutputMode.DEBUG);

        assertEquals(graph.nodes().size(), graphMode.graph().nodes().size());
        assertEquals(graph.edges().size(), graphMode.graph().edges().size());
        assertEquals(graph.nodes().size(), debugMode.graph().nodes().size());
        assertEquals(graph.edges().size(), debugMode.graph().edges().size());
        assertTrue(hasClassItem(debugMode, "com.example.orders.TechnicalHelper"));
    }

    @Test
    void shouldFilterBusinessModeToBusinessRelevantNodesAndEdges() {
        var compressed = compressorService.compress(endpoint(), graphBuildResult(graph(), List.of(), limits(false, false)),
                GitLabEndpointUseCaseOutputMode.BUSINESS);

        assertTrue(hasClassItem(compressed, "com.example.orders.OrderController"));
        assertTrue(hasClassItem(compressed, "com.example.orders.SubmitOrderUseCase"));
        assertTrue(hasClassItem(compressed, "com.example.orders.OrderRepository"));
        assertTrue(hasClassItem(compressed, "com.example.payments.PaymentGateway"));
        assertFalse(hasClassItem(compressed, "com.example.orders.OrderMapper"));
        assertFalse(hasClassItem(compressed, "com.example.orders.TechnicalHelper"));

        assertTrue(hasEdge(compressed.graph(), GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE));
        assertTrue(hasEdge(compressed.graph(), GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL));
        assertFalse(hasEdge(compressed.graph(), GitLabEndpointUseCaseEdgeKind.MAPPING));
    }

    @Test
    void shouldLowerConfidenceWhenLimitsOrResolutionWarningsArePresent() {
        var maxNodesLimited = compressorService.compress(endpoint(),
                graphBuildResult(graph(), List.of(), limits(false, true)),
                GitLabEndpointUseCaseOutputMode.COMPACT);
        var unresolvedWarning = compressorService.compress(endpoint(),
                graphBuildResult(graph(), List.of(new GitLabEndpointUseCaseWarning(
                        GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED,
                        GitLabEndpointUseCaseWarningSeverity.WARNING,
                        "Unresolved call.",
                        "src/main/java/com/example/orders/SubmitOrderUseCase.java",
                        42,
                        List.of()
                )), limits(false, false)),
                GitLabEndpointUseCaseOutputMode.COMPACT);

        assertEquals(GitLabEndpointUseCaseConfidence.LOW, maxNodesLimited.confidence());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, unresolvedWarning.confidence());
    }

    private GitLabEndpointUseCaseEndpointCandidate endpoint() {
        return new GitLabEndpointUseCaseEndpointCandidate(
                "POST /api/orders/{id}/submit -> com.example.orders.OrderController#submit",
                List.of("POST"),
                "/api/orders/{id}/submit",
                "/api/orders/{id}/submit",
                "com.example.orders.OrderController",
                "submit",
                "com.example.orders.OrderController#submit(String)",
                "src/main/java/com/example/orders/OrderController.java",
                20,
                24,
                List.of(),
                List.of(),
                List.of("PostMapping"),
                GitLabEndpointUseCaseConfidence.HIGH,
                List.of()
        );
    }

    private GitLabEndpointUseCaseGraphBuildResult graphBuildResult(
            GitLabEndpointUseCaseGraph graph,
            List<GitLabEndpointUseCaseWarning> warnings,
            GitLabEndpointUseCaseLimits limits
    ) {
        return new GitLabEndpointUseCaseGraphBuildResult(graph, limits, warnings);
    }

    private GitLabEndpointUseCaseLimits limits(boolean maxDepthReached, boolean maxNodesReached) {
        return new GitLabEndpointUseCaseLimits(8, 80, maxDepthReached, maxNodesReached);
    }

    private GitLabEndpointUseCaseGraph graph() {
        return new GitLabEndpointUseCaseGraph(
                List.of(
                        node("com.example.orders.OrderController#submit(String)", "com.example.orders.OrderController",
                                "submit(String)", GitLabEndpointUseCaseRole.CONTROLLER, 0, false, null),
                        node("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.orders.SubmitOrderUseCase", "submit(String)",
                                GitLabEndpointUseCaseRole.USE_CASE_SERVICE, 1, false, null),
                        node("com.example.orders.OrderValidator#validate(String)", "com.example.orders.OrderValidator",
                                "validate(String)", GitLabEndpointUseCaseRole.VALIDATOR, 2, false, null),
                        node("com.example.orders.OrderMapper#map(Order)", "com.example.orders.OrderMapper",
                                "map(Order)", GitLabEndpointUseCaseRole.MAPPER, 2, false, null),
                        node("com.example.orders.OrderRepository#save(Order)", "com.example.orders.OrderRepository",
                                "save(Order)", GitLabEndpointUseCaseRole.REPOSITORY, 2, true,
                                "Repository boundary."),
                        node("com.example.payments.PaymentGateway#capture(String)",
                                "com.example.payments.PaymentGateway", "capture(String)",
                                GitLabEndpointUseCaseRole.EXTERNAL_CLIENT, 2, true,
                                "External client boundary."),
                        node("com.example.orders.TechnicalHelper#trace()", "com.example.orders.TechnicalHelper",
                                "trace()", GitLabEndpointUseCaseRole.UNKNOWN, 2, false, null)
                ),
                List.of(
                        edge("com.example.orders.OrderController#submit(String)",
                                "com.example.orders.SubmitOrderUseCase#submit(String)",
                                GitLabEndpointUseCaseEdgeKind.SYNC_CALL,
                                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN,
                                "useCase.submit(id)"),
                        edge("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.orders.OrderValidator#validate(String)",
                                GitLabEndpointUseCaseEdgeKind.VALIDATION,
                                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN,
                                "validator.validate(id)"),
                        edge("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.orders.OrderMapper#map(Order)",
                                GitLabEndpointUseCaseEdgeKind.MAPPING,
                                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN,
                                "mapper.map(order)"),
                        edge("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.orders.OrderRepository#save(Order)",
                                GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE,
                                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN,
                                "repository.save(order)"),
                        edge("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.payments.PaymentGateway#capture(String)",
                                GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL,
                                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN,
                                "paymentGateway.capture(id)"),
                        edge("com.example.orders.SubmitOrderUseCase#submit(String)",
                                "com.example.orders.TechnicalHelper#trace()",
                                GitLabEndpointUseCaseEdgeKind.SYNC_CALL,
                                GitLabEndpointUseCaseResolutionKind.DIRECT_METHOD,
                                "trace()")
                )
        );
    }

    private GitLabEndpointUseCaseNode node(
            String id,
            String classFqn,
            String methodSignature,
            GitLabEndpointUseCaseRole role,
            int depth,
            boolean terminal,
            String terminalReason
    ) {
        return new GitLabEndpointUseCaseNode(
                id,
                GitLabEndpointUseCaseNodeKind.METHOD,
                classFqn,
                methodSignature,
                role,
                depth,
                "src/main/java/" + classFqn.replace('.', '/') + ".java",
                10 + depth,
                12 + depth,
                terminal,
                terminalReason
        );
    }

    private GitLabEndpointUseCaseEdge edge(
            String from,
            String to,
            GitLabEndpointUseCaseEdgeKind kind,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            String call
    ) {
        return new GitLabEndpointUseCaseEdge(
                from,
                to,
                kind,
                resolutionKind,
                call,
                42,
                GitLabEndpointUseCaseConfidence.HIGH,
                false
        );
    }

    private static void assertClassItem(
            GitLabEndpointUseCaseCompressedContext compressed,
            String classFqn,
            GitLabEndpointUseCaseRole role
    ) {
        var item = compressed.classList().stream()
                .filter(candidate -> classFqn.equals(candidate.classFqn()))
                .findFirst()
                .orElseThrow();
        assertEquals(role, item.role());
    }

    private static boolean hasClassItem(GitLabEndpointUseCaseCompressedContext compressed, String classFqn) {
        return compressed.classList().stream().anyMatch(candidate -> classFqn.equals(candidate.classFqn()));
    }

    private static boolean hasEdge(GitLabEndpointUseCaseGraph graph, GitLabEndpointUseCaseEdgeKind edgeKind) {
        return graph.edges().stream().anyMatch(edge -> edge.kind() == edgeKind);
    }
}
