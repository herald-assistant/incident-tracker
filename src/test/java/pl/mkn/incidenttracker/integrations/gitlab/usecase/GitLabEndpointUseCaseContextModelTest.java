package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabEndpointUseCaseContextModelTest {

    @Test
    void shouldApplyRequestDefaults() {
        var request = new GitLabEndpointUseCaseContextRequest(
                "orders-api",
                null,
                "POST",
                "/api/orders/123/submit",
                "/src/main/java/",
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("src/main/java", request.sourcePathPrefix());
        assertEquals(GitLabEndpointUseCaseOutputMode.COMPACT, request.outputMode());
        assertEquals(GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH, request.maxDepth());
        assertEquals(GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_NODES, request.maxNodes());
        assertFalse(request.includeAsyncConsumers());
    }

    @Test
    void shouldKeepResultListsImmutableAndNullSafe() {
        var nodes = new ArrayList<GitLabEndpointUseCaseNode>();
        nodes.add(new GitLabEndpointUseCaseNode(
                "n1",
                GitLabEndpointUseCaseNodeKind.METHOD,
                "pl.mkn.orders.OrderController",
                "submit(String)",
                GitLabEndpointUseCaseRole.CONTROLLER,
                0,
                "src/main/java/pl/mkn/orders/OrderController.java",
                42,
                58,
                false,
                null
        ));
        var methods = new ArrayList<>(List.of("submit(String)"));
        var classItems = new ArrayList<GitLabEndpointUseCaseClassItem>();
        classItems.add(new GitLabEndpointUseCaseClassItem(
                "pl.mkn.orders.OrderController",
                GitLabEndpointUseCaseRole.CONTROLLER,
                0,
                methods,
                false,
                "Endpoint handler."
        ));
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>();
        warnings.add(new GitLabEndpointUseCaseWarning(
                "BRANCH_REF_NOT_IMMUTABLE",
                GitLabEndpointUseCaseWarningSeverity.INFO,
                "MVP analysis is branch-based.",
                null,
                null,
                List.of()
        ));

        var result = new GitLabEndpointUseCaseContextResult(
                null,
                null,
                null,
                new GitLabEndpointUseCaseGraph(nodes, null),
                classItems,
                warnings,
                null,
                null,
                null,
                null
        );
        nodes.clear();
        methods.add("ignored()");
        classItems.clear();
        warnings.clear();

        assertEquals(1, result.graph().nodes().size());
        assertEquals(List.of(), result.graph().edges());
        assertEquals(1, result.classList().size());
        assertEquals(List.of("submit(String)"), result.classList().get(0).methods());
        assertEquals(1, result.warnings().size());
        assertEquals(List.of(), result.evidence());
        assertEquals(List.of(), result.suggestedNextReads());
        assertEquals(GitLabEndpointUseCaseLimits.defaults(), result.limits());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, result.confidence());

        assertThrows(UnsupportedOperationException.class, () -> result.classList().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.graph().nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.classList().get(0).methods().clear());
    }
}
