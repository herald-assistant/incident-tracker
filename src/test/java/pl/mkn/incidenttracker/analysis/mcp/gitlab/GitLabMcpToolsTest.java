package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabMcpToolsTest {

    private final GitLabMcpTools gitLabMcpTools = new GitLabMcpTools(new TestGitLabRepositoryPort());

    @Test
    void shouldSearchRepositoryCandidatesThroughTool() {
        var response = gitLabMcpTools.searchRepositoryCandidates(
                "timeout-123",
                "sample/runtime",
                "release/2026.04",
                List.of("billing-service", "catalog-service"),
                List.of("GET /inventory"),
                List.of("timeout", "inventory")
        );

        assertEquals(1, response.candidates().size());
        assertEquals("sample/runtime", response.candidates().get(0).group());
        assertEquals("edge-client-service", response.candidates().get(0).projectName());
        assertEquals("release/2026.04", response.candidates().get(0).branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", response.candidates().get(0).filePath());
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughToolUsingComponentHintForNestedProject() {
        var response = gitLabMcpTools.searchRepositoryCandidates(
                "agreement-123",
                "TENANT-ALPHA",
                "release-candidate",
                List.of("document-workflow"),
                List.of(),
                List.of("document")
        );

        assertEquals(1, response.candidates().size());
        assertEquals("TENANT-ALPHA", response.candidates().get(0).group());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", response.candidates().get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/DocumentWorkflowService.java", response.candidates().get(0).filePath());
    }

    @Test
    void shouldReadRepositoryFileThroughTool() {
        var response = gitLabMcpTools.readRepositoryFile(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        );

        assertEquals("sample/runtime", response.group());
        assertEquals("edge-client-service", response.projectName());
        assertEquals("release/2026.04", response.branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", response.filePath());
        assertTrue(response.content().contains("CatalogGatewayClient"));
        assertTrue(response.truncated());
    }

    @Test
    void shouldUseDefaultCharacterLimitWhenNotProvided() {
        var response = gitLabMcpTools.readRepositoryFile(
                "sample/runtime",
                "ledger-write-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java",
                null
        );

        assertFalse(response.truncated());
        assertTrue(response.content().contains("LedgerTransactionService"));
    }

    @Test
    void shouldReadRepositoryFileChunkThroughTool() {
        var response = gitLabMcpTools.readRepositoryFileChunk(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        );

        assertEquals("sample/runtime", response.group());
        assertEquals("release/2026.04", response.branch());
        assertEquals(5, response.requestedStartLine());
        assertEquals(12, response.requestedEndLine());
        assertEquals(5, response.returnedStartLine());
        assertEquals(12, response.returnedEndLine());
        assertEquals(14, response.totalLines());
        assertTrue(response.content().contains("return catalogWebClient.get()"));
        assertFalse(response.truncated());
    }

}

