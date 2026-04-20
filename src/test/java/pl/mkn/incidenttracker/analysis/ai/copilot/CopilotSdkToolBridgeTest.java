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

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }

}

