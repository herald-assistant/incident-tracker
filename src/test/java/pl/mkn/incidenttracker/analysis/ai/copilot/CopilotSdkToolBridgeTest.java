package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceCaptureRegistry;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabMcpTools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkToolBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeSpringToolsAsCopilotToolDefinitions() {
        var bridge = new CopilotSdkToolBridge(
                List.of(gitLabToolProvider()),
                objectMapper,
                new CopilotToolEvidenceCaptureRegistry(objectMapper)
        );

        var tools = bridge.buildToolDefinitions();

        assertEquals(6, tools.size());
        assertEquals(
                Set.of(
                        "gitlab_find_flow_context",
                        "gitlab_search_repository_candidates",
                        "gitlab_read_repository_file",
                        "gitlab_read_repository_file_chunk",
                        "gitlab_read_repository_file_chunks",
                        "gitlab_read_repository_file_outline"
                ),
                tools.stream().map(tool -> tool.name()).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void shouldInvokeSpringGitLabChunkToolThroughCopilotToolDefinition() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var bridge = new CopilotSdkToolBridge(List.of(gitLabToolProvider()), objectMapper, registry);
        var tool = bridge.buildToolDefinitions().stream()
                .filter(candidate -> candidate.name().equals("gitlab_read_repository_file_chunk"))
                .findFirst()
                .orElseThrow();
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-123", new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        });

        var invocation = new ToolInvocation()
                .setSessionId("session-123")
                .setToolName("gitlab_read_repository_file_chunk")
                .setArguments(objectMapper.valueToTree(Map.of(
                        "group", "sample/runtime",
                        "projectName", "edge-client-service",
                        "branch", "release/2026.04",
                        "filePath", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                        "startLine", 5,
                        "endLine", 12,
                        "maxCharacters", 4_000
                )));

        var result = tool.handler().invoke(invocation).join();

        assertInstanceOf(Map.class, result);

        @SuppressWarnings("unchecked")
        var chunkResult = (Map<String, Object>) result;

        assertEquals("sample/runtime", chunkResult.get("group"));
        assertEquals("edge-client-service", chunkResult.get("projectName"));
        assertEquals("release/2026.04", chunkResult.get("branch"));
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", chunkResult.get("filePath"));
        assertEquals(5, chunkResult.get("returnedStartLine"));
        assertEquals(12, chunkResult.get("returnedEndLine"));
        assertEquals(14, chunkResult.get("totalLines"));
        assertTrue(chunkResult.get("content").toString().contains("timeout(Duration.ofSeconds(2))"));
        assertFalse((Boolean) chunkResult.get("truncated"));
        assertNotNull(capturedSection.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-fetched-code", capturedSection.get().category());
        assertEquals(1, capturedSection.get().items().size());
        assertTrue(capturedSection.get().items().get(0).title().contains("CatalogGatewayClient.java"));
    }

    @Test
    void shouldInvokeSpringGitLabChunksToolThroughCopilotToolDefinition() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var bridge = new CopilotSdkToolBridge(List.of(gitLabToolProvider()), objectMapper, registry);
        var tool = bridge.buildToolDefinitions().stream()
                .filter(candidate -> candidate.name().equals("gitlab_read_repository_file_chunks"))
                .findFirst()
                .orElseThrow();
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-456", new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        });

        var invocation = new ToolInvocation()
                .setSessionId("session-456")
                .setToolName("gitlab_read_repository_file_chunks")
                .setArguments(objectMapper.valueToTree(Map.of(
                        "group", "sample/runtime",
                        "branch", "release/2026.04",
                        "chunks", List.of(
                                Map.of(
                                        "projectName", "edge-client-service",
                                        "filePath", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                                        "startLine", 5,
                                        "endLine", 12,
                                        "maxCharacters", 4_000
                                ),
                                Map.of(
                                        "projectName", "ledger-write-service",
                                        "filePath", "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java",
                                        "startLine", 4,
                                        "endLine", 8,
                                        "maxCharacters", 4_000
                                )
                        ),
                        "maxTotalCharacters", 20_000
                )));

        var result = tool.handler().invoke(invocation).join();

        assertInstanceOf(Map.class, result);

        @SuppressWarnings("unchecked")
        var chunksResult = (Map<String, Object>) result;
        @SuppressWarnings("unchecked")
        var returnedChunks = (List<Map<String, Object>>) chunksResult.get("chunks");

        assertEquals(2, returnedChunks.size());
        assertEquals("edge-client-service", returnedChunks.get(0).get("projectName"));
        assertEquals("ledger-write-service", returnedChunks.get(1).get("projectName"));
        assertNotNull(capturedSection.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-fetched-code", capturedSection.get().category());
        assertEquals(2, capturedSection.get().items().size());
    }

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }

}

