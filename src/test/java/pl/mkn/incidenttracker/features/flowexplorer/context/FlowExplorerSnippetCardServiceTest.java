package pl.mkn.incidenttracker.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerSnippetCardServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final FlowExplorerSnippetCardService service = new FlowExplorerSnippetCardService(repositoryPort);

    @Test
    void shouldBuildSnippetCardsForPrioritizedFlowNodes() {
        when(repositoryPort.readFileChunk(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> chunk(
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        "public void selectedMethod() {\n    service.call();\n}",
                        false
                ));

        var result = service.buildSnippetCards(
                "platform/backend",
                "feature/FLOW-42",
                repository(),
                List.of(
                        node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24),
                        node("src/main/java/app/CustomerService.java", "USE_CASE_SERVICE", "getCustomer", 40, 65),
                        node("src/main/java/app/CustomerRepository.java", "SPRING_DATA_REPOSITORY", "findCustomer", 8, 12),
                        node("src/main/java/app/CustomerMapper.java", "MAPPER", "toResponse", 20, 32)
                ),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of(FlowExplorerFocusArea.PERSISTENCE)
        );

        assertEquals(3, result.cards().size());
        assertTrue(result.budgetReached());
        assertEquals("CONTROLLER", result.cards().get(0).role());
        assertEquals("USE_CASE_SERVICE", result.cards().get(1).role());
        assertEquals("SPRING_DATA_REPOSITORY", result.cards().get(2).role());
        assertTrue(result.cards().get(0).content().contains("// ... omitted earlier lines ..."));
        assertTrue(result.cards().get(0).content().contains("public void selectedMethod"));
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("Snippet card budget reached")));
        verify(repositoryPort, times(3)).readFileChunk(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void shouldSurfaceReadFailuresAsLimitations() {
        when(repositoryPort.readFileChunk(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("GitLab unavailable"));

        var result = service.buildSnippetCards(
                "platform/backend",
                "main",
                repository(),
                List.of(node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24)),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of()
        );

        assertTrue(result.cards().isEmpty());
        assertFalse(result.budgetReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("GitLab unavailable")));
    }

    @Test
    void shouldSkipSnippetCardsWhenRepositoryIsMissing() {
        var result = service.buildSnippetCards(
                "platform/backend",
                "main",
                null,
                List.of(node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24)),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of()
        );

        assertTrue(result.cards().isEmpty());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("selected repository is not resolved")));
    }

    private static FlowExplorerRepositoryContext repository() {
        return new FlowExplorerRepositoryContext(
                "crm-service",
                "crm-service",
                "platform/backend/crm-service",
                "feature/FLOW-42",
                true,
                true,
                List.of()
        );
    }

    private static FlowExplorerFlowNode node(String filePath, String role, String methodName, int lineStart, int lineEnd) {
        return new FlowExplorerFlowNode(
                filePath,
                role,
                filePath,
                List.of(new FlowExplorerFlowMethod(methodName, lineStart, lineEnd)),
                "Selected because it participates in endpoint flow.",
                "HIGH",
                List.of()
        );
    }

    private static GitLabRepositoryFileChunk chunk(
            String filePath,
            int startLine,
            int endLine,
            String content,
            boolean truncated
    ) {
        return new GitLabRepositoryFileChunk(
                "platform/backend",
                "crm-service",
                "feature/FLOW-42",
                filePath,
                startLine,
                endLine,
                startLine,
                endLine,
                endLine + 20,
                content,
                truncated
        );
    }
}
