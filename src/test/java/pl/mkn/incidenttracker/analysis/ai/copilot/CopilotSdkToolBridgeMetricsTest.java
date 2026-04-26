package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolMetrics;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolBridge;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotSdkToolBridgeMetricsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldClassifyToolGroupsByName() {
        assertEquals("elasticsearch", CopilotToolMetrics.toolGroup("elastic_search_logs_by_correlation_id"));
        assertEquals("gitlab", CopilotToolMetrics.toolGroup("gitlab_read_repository_file"));
        assertEquals("database", CopilotToolMetrics.toolGroup("db_count_rows"));
        assertEquals("other", CopilotToolMetrics.toolGroup("context_echo"));
        assertEquals("unknown", CopilotToolMetrics.toolGroup(null));
    }

    @Test
    void shouldRecordToolMetricsByGroupAndExpensiveToolCounters() {
        var properties = new CopilotMetricsProperties();
        properties.setLogToolEvents(false);
        var metricsRegistry = new CopilotSessionMetricsRegistry(properties);
        var bridge = toolBridge(
                List.of(metricsToolProvider()),
                objectMapper,
                toolEvidenceCaptureRegistry(objectMapper),
                metricsRegistry,
                new CopilotMetricsLogger(properties, objectMapper)
        );
        var context = sessionContext();
        metricsRegistry.recordPreparation(
                context,
                new AnalysisAiAnalysisRequest("corr-123", "zt01", "main", "sample/runtime", List.of()),
                List.of(),
                "prompt",
                1L
        );
        var toolsByName = bridge.buildToolDefinitions(context).stream()
                .collect(java.util.stream.Collectors.toMap(ToolDefinition::name, tool -> tool));

        invoke(toolsByName.get("gitlab_read_repository_file"), context, "tool-gitlab");
        invoke(toolsByName.get("db_execute_readonly_sql"), context, "tool-db");

        var metrics = metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow();

        assertEquals(2, metrics.totalToolCalls());
        assertEquals(1, metrics.gitLabToolCalls());
        assertEquals(1, metrics.databaseToolCalls());
        assertEquals(1, metrics.gitLabReadFileCalls());
        assertEquals(0, metrics.gitLabReadChunkCalls());
        assertEquals(1, metrics.databaseQueryCalls());
        assertEquals(1, metrics.databaseRawSqlCalls());
        assertTrue(metrics.gitLabReturnedCharacters() > 0L);
        assertTrue(metrics.databaseReturnedCharacters() > 0L);
    }

    private void invoke(ToolDefinition tool, CopilotToolSessionContext context, String toolCallId) {
        tool.handler().invoke(new ToolInvocation()
                .setSessionId(context.copilotSessionId())
                .setToolCallId(toolCallId)
                .setToolName(tool.name())
                .setArguments(objectMapper.valueToTree(Map.of()))).join();
    }

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime"
        );
    }

    private ToolCallbackProvider metricsToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new MetricsTools())
                .build();
    }

    static class MetricsTools {

        @Tool(name = "gitlab_read_repository_file", description = "Read repository file for metrics tests.")
        public Map<String, Object> readGitLabFile() {
            return Map.of(
                    "projectName", "orders-api",
                    "content", "class OrdersApi {}"
            );
        }

        @Tool(name = "db_execute_readonly_sql", description = "Run readonly SQL for metrics tests.")
        public Map<String, Object> executeReadonlySql() {
            return Map.of(
                    "rows", List.of(Map.of("ID", "1")),
                    "warnings", Set.of()
            );
        }
    }
}
