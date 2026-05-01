package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabMcpTools;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkToolFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeSpringToolsAsCopilotToolDefinitions() {
        var factory = factory(List.of(gitLabToolProvider()));
        var analysisRunId = UUID.randomUUID().toString();
        var tools = factory.createToolDefinitions(new CopilotToolSessionContext(
                analysisRunId,
                "analysis-" + analysisRunId,
                null,
                null,
                null,
                null
        ));

        assertEquals(7, tools.size());
        assertEquals(
                Set.of(
                        "gitlab_find_class_references",
                        "gitlab_find_flow_context",
                        "gitlab_search_repository_candidates",
                        "gitlab_read_repository_file",
                        "gitlab_read_repository_file_chunk",
                        "gitlab_read_repository_file_chunks",
                        "gitlab_read_repository_file_outline"
                ),
                tools.stream().map(ToolDefinition::name).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void shouldPassSessionBoundContextToSpringToolsThroughBridge() {
        var factory = factory(List.of(contextEchoToolProvider()));
        var sessionContext = gitLabSessionContext();
        var tool = factory.createToolDefinitions(sessionContext).stream()
                .filter(candidate -> candidate.name().equals("context_echo"))
                .findFirst()
                .orElseThrow();
        var invocation = new ToolInvocation()
                .setSessionId(sessionContext.copilotSessionId())
                .setToolCallId("tool-call-ctx-1")
                .setToolName("context_echo")
                .setArguments(objectMapper.valueToTree(Map.of("projectName", "orders-api")));

        var result = tool.handler().invoke(invocation).join();

        assertInstanceOf(Map.class, result);

        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;

        assertEquals("orders-api", payload.get("projectName"));
        assertEquals("corr-123", payload.get("correlationId"));
        assertEquals("sample/runtime", payload.get("gitLabGroup"));
        assertEquals("release/2026.04", payload.get("gitLabBranch"));
        assertEquals("zt01", payload.get("environment"));
        assertEquals("incident-analysis", payload.get("featureName"));
        assertEquals("analysis-run-1", payload.get("copilotSessionId"));
        assertEquals("analysis-run-1", payload.get("actualCopilotSessionId"));
        assertEquals("tool-call-ctx-1", payload.get("toolCallId"));
        assertEquals("context_echo", payload.get("toolName"));
    }

    @Test
    void shouldRejectInvocationWhenSessionIdDoesNotMatchContext() {
        var factory = factory(List.of(contextEchoToolProvider()));
        var tool = factory.createToolDefinitions(gitLabSessionContext()).stream()
                .filter(candidate -> candidate.name().equals("context_echo"))
                .findFirst()
                .orElseThrow();
        var invocation = new ToolInvocation()
                .setSessionId("analysis-other")
                .setToolCallId("tool-call-ctx-2")
                .setToolName("context_echo")
                .setArguments(objectMapper.valueToTree(Map.of("projectName", "orders-api")));

        var exception = assertThrows(CompletionException.class, () -> tool.handler().invoke(invocation).join());

        assertTrue(exception.getCause().getMessage().contains("Copilot tool invocation sessionId mismatch"));
    }

    @Test
    void shouldReturnFailedFutureWhenSpringToolCallbackFails() {
        var factory = factory(List.of(failingToolProvider()));
        var sessionContext = gitLabSessionContext();
        var tool = factory.createToolDefinitions(sessionContext).stream()
                .filter(candidate -> candidate.name().equals("failing_tool"))
                .findFirst()
                .orElseThrow();
        var invocation = new ToolInvocation()
                .setSessionId(sessionContext.copilotSessionId())
                .setToolCallId("tool-call-failing-1")
                .setToolName("failing_tool")
                .setArguments(objectMapper.valueToTree(Map.of()));

        var exception = assertThrows(CompletionException.class, () -> tool.handler().invoke(invocation).join());

        assertTrue(exception.getCause().getMessage().contains("boom"));
    }

    @Test
    void shouldHideSessionBoundFieldsFromGitLabToolSchemas() {
        var factory = factory(List.of(gitLabToolProvider()));
        var toolsByName = factory.createToolDefinitions(gitLabSessionContext()).stream()
                .collect(java.util.stream.Collectors.toMap(ToolDefinition::name, tool -> tool));

        assertSchemaProperties(
                toolsByName.get("gitlab_search_repository_candidates"),
                Set.of("projectNames", "operationNames", "keywords", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_read_repository_file"),
                Set.of("projectName", "filePath", "maxCharacters", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_read_repository_file_chunk"),
                Set.of("projectName", "filePath", "startLine", "endLine", "maxCharacters", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_read_repository_file_outline"),
                Set.of("projectName", "filePath", "maxCharacters", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_read_repository_file_chunks"),
                Set.of("chunks", "maxTotalCharacters", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_find_class_references"),
                Set.of("projectNames", "className", "relatedHints", "operationNames", "maxFilesPerRole", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
        assertSchemaProperties(
                toolsByName.get("gitlab_find_flow_context"),
                Set.of("projectNames", "keywords", "operationNames", "maxFilesPerRole", "reason"),
                Set.of("group", "branch", "correlationId", "toolContext")
        );
    }

    @Test
    void shouldInvokeSpringGitLabChunkToolThroughCopilotToolDefinition() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var factory = factory(List.of(gitLabToolProvider()), registry);
        var sessionContext = gitLabSessionContext();
        var tool = factory.createToolDefinitions(sessionContext).stream()
                .filter(candidate -> candidate.name().equals("gitlab_read_repository_file_chunk"))
                .findFirst()
                .orElseThrow();
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession(sessionContext.copilotSessionId(), capturedSection::set);

        var invocation = new ToolInvocation()
                .setSessionId(sessionContext.copilotSessionId())
                .setToolCallId("tool-call-1")
                .setToolName("gitlab_read_repository_file_chunk")
                .setArguments(objectMapper.valueToTree(Map.of(
                        "projectName", "edge-client-service",
                        "filePath", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                        "startLine", 5,
                        "endLine", 12,
                        "maxCharacters", 4_000,
                        "reason", "Sprawdzam fragment klienta z timeoutem."
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
        var registry = toolEvidenceSessionStore(objectMapper);
        var factory = factory(List.of(gitLabToolProvider()), registry);
        var sessionContext = gitLabSessionContext();
        var tool = factory.createToolDefinitions(sessionContext).stream()
                .filter(candidate -> candidate.name().equals("gitlab_read_repository_file_chunks"))
                .findFirst()
                .orElseThrow();
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession(sessionContext.copilotSessionId(), capturedSection::set);

        var invocation = new ToolInvocation()
                .setSessionId(sessionContext.copilotSessionId())
                .setToolCallId("tool-call-2")
                .setToolName("gitlab_read_repository_file_chunks")
                .setArguments(objectMapper.valueToTree(Map.of(
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
                        "maxTotalCharacters", 20_000,
                        "reason", "Sprawdzam dwa fragmenty powiazane z przeplywem."
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

    private void assertSchemaProperties(
            ToolDefinition tool,
            Set<String> expectedProperties,
            Set<String> forbiddenProperties
    ) {
        assertNotNull(tool);
        assertInstanceOf(Map.class, tool.parameters());

        @SuppressWarnings("unchecked")
        var parameters = (Map<String, Object>) tool.parameters();
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) parameters.get("properties");

        assertNotNull(properties);
        assertTrue(properties.keySet().containsAll(expectedProperties));
        forbiddenProperties.forEach(property -> assertFalse(properties.containsKey(property)));
    }

    private CopilotToolSessionContext gitLabSessionContext() {
        var hiddenContext = new LinkedHashMap<String, Object>();
        hiddenContext.put(AgentToolContextKeys.CORRELATION_ID, "corr-123");
        hiddenContext.put(AgentToolContextKeys.ENVIRONMENT, "zt01");
        hiddenContext.put(AgentToolContextKeys.GITLAB_BRANCH, "release/2026.04");
        hiddenContext.put(AgentToolContextKeys.GITLAB_GROUP, "sample/runtime");
        hiddenContext.put("featureName", "incident-analysis");

        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                hiddenContext
        );
    }

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }

    private ToolCallbackProvider contextEchoToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new ContextEchoTools())
                .build();
    }

    private ToolCallbackProvider failingToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new FailingTools())
                .build();
    }

    private CopilotSdkToolFactory factory(List<ToolCallbackProvider> providers) {
        return factory(providers, toolEvidenceSessionStore(objectMapper));
    }

    private CopilotSdkToolFactory factory(
            List<ToolCallbackProvider> providers,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore
    ) {
        return toolFactory(
                providers,
                objectMapper,
                toolEvidenceSessionStore,
                new CopilotSessionMetricsRegistry(new CopilotMetricsProperties()),
                new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper)
        );
    }

    static class ContextEchoTools {

        @Tool(name = "context_echo", description = "Echoes selected ToolContext values for testing.")
        public Map<String, Object> contextEcho(
                @ToolParam(description = "GitLab project path used only to verify normal args still work.")
                String projectName,
                ToolContext toolContext
        ) {
            var context = toolContext.getContext();
            return Map.of(
                    "projectName", projectName,
                    "correlationId", context.get(AgentToolContextKeys.CORRELATION_ID),
                    "gitLabGroup", context.get(AgentToolContextKeys.GITLAB_GROUP),
                    "gitLabBranch", context.get(AgentToolContextKeys.GITLAB_BRANCH),
                    "environment", context.get(AgentToolContextKeys.ENVIRONMENT),
                    "featureName", context.get("featureName"),
                    "copilotSessionId", context.get(AgentToolContextKeys.COPILOT_SESSION_ID),
                    "actualCopilotSessionId", context.get(AgentToolContextKeys.ACTUAL_COPILOT_SESSION_ID),
                    "toolCallId", context.get(AgentToolContextKeys.TOOL_CALL_ID),
                    "toolName", context.get(AgentToolContextKeys.TOOL_NAME)
            );
        }
    }

    static class FailingTools {

        @Tool(name = "failing_tool", description = "Throws for failed future testing.")
        public Map<String, Object> failingTool() {
            throw new IllegalStateException("boom");
        }
    }
}
