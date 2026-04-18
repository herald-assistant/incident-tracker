package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabMcpTools;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkToolBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeSpringToolsAsCopilotToolDefinitions() {
        var bridge = new CopilotSdkToolBridge(List.of(gitLabToolProvider()), objectMapper);

        var tools = bridge.buildToolDefinitions();

        assertEquals(3, tools.size());
        assertEquals(
                Set.of(
                        "gitlab_search_repository_candidates",
                        "gitlab_read_repository_file",
                        "gitlab_read_repository_file_chunk"
                ),
                tools.stream().map(tool -> tool.name()).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void shouldInvokeSpringGitLabChunkToolThroughCopilotToolDefinition() {
        var bridge = new CopilotSdkToolBridge(List.of(gitLabToolProvider()), objectMapper);
        var tool = bridge.buildToolDefinitions().stream()
                .filter(candidate -> candidate.name().equals("gitlab_read_repository_file_chunk"))
                .findFirst()
                .orElseThrow();

        var invocation = new ToolInvocation()
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
    }

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }

}

