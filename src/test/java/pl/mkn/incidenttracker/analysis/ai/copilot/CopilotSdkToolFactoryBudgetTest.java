package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.BudgetMode;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetPolicy;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkToolFactoryBudgetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnControlledDeniedResultInHardModeWithoutInvokingCallback() {
        var tools = new BudgetTestTools();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.HARD);
        properties.setMaxDbRawSqlCalls(0);
        var metricsRegistry = metricsRegistry();
        var budgetRegistry = new CopilotToolBudgetRegistry(properties);
        var factory = factory(tools, budgetRegistry, metricsRegistry);
        var context = registerSession(budgetRegistry, metricsRegistry);
        var tool = toolByName(factory, context, "db_execute_readonly_sql");

        var result = invoke(tool, context, "tool-db-1");

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;
        assertEquals("denied_by_tool_budget", payload.get("status"));
        assertEquals("db_execute_readonly_sql", payload.get("toolName"));
        assertTrue(payload.get("reason").toString().contains("Database raw SQL tool call budget exceeded"));
        assertEquals(0, tools.rawSqlCalls.get());

        var metrics = metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow();
        assertEquals(1, metrics.budgetRawSqlAttempts());
        assertEquals(1, metrics.budgetDeniedToolCalls());
        assertEquals(0, metrics.databaseRawSqlCalls());
    }

    @Test
    void shouldAllowToolCallInSoftModeAndRecordWarning() {
        var tools = new BudgetTestTools();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.SOFT);
        properties.setMaxTotalCalls(0);
        var metricsRegistry = metricsRegistry();
        var budgetRegistry = new CopilotToolBudgetRegistry(properties);
        var factory = factory(tools, budgetRegistry, metricsRegistry);
        var context = registerSession(budgetRegistry, metricsRegistry);
        var tool = toolByName(factory, context, "gitlab_read_repository_file");

        var result = invoke(tool, context, "tool-gitlab-1");

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;
        assertEquals("ok", payload.get("status"));
        assertEquals(1, tools.gitLabReadFileCalls.get());
        var metrics = metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow();
        assertTrue(metrics.budgetSoftLimitExceededCount() > 0);
        assertEquals(1, metrics.gitLabReadFileCalls());
    }

    private CopilotSdkToolFactory factory(
            BudgetTestTools tools,
            CopilotToolBudgetRegistry budgetRegistry,
            CopilotSessionMetricsRegistry metricsRegistry
    ) {
        var metricsProperties = new CopilotMetricsProperties();
        metricsProperties.setLogToolEvents(false);
        return toolFactory(
                List.of(MethodToolCallbackProvider.builder().toolObjects(tools).build()),
                objectMapper,
                toolEvidenceSessionStore(objectMapper),
                metricsRegistry,
                new CopilotMetricsLogger(metricsProperties, objectMapper),
                new CopilotToolBudgetPolicy(budgetRegistry, metricsRegistry)
        );
    }

    private CopilotToolSessionContext registerSession(
            CopilotToolBudgetRegistry budgetRegistry,
            CopilotSessionMetricsRegistry metricsRegistry
    ) {
        var context = new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "sample/runtime"
        );
        budgetRegistry.registerSession(context.copilotSessionId());
        metricsRegistry.recordPreparation(
                context,
                new InitialAnalysisRequest("corr-123", "zt01", "main", "sample/runtime", List.of()),
                List.of(),
                "prompt",
                1L
        );
        return context;
    }

    private CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }

    private ToolDefinition toolByName(
            CopilotSdkToolFactory factory,
            CopilotToolSessionContext context,
            String toolName
    ) {
        return factory.createToolDefinitions(context).stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElseThrow();
    }

    private Object invoke(ToolDefinition tool, CopilotToolSessionContext context, String toolCallId) {
        return tool.handler().invoke(new ToolInvocation()
                .setSessionId(context.copilotSessionId())
                .setToolCallId(toolCallId)
                .setToolName(tool.name())
                .setArguments(objectMapper.valueToTree(Map.of()))).join();
    }

    static class BudgetTestTools {
        private final AtomicInteger rawSqlCalls = new AtomicInteger();
        private final AtomicInteger gitLabReadFileCalls = new AtomicInteger();

        @Tool(name = "db_execute_readonly_sql", description = "Raw SQL test tool.")
        public Map<String, Object> executeReadonlySql() {
            rawSqlCalls.incrementAndGet();
            return Map.of("status", "ok");
        }

        @Tool(name = "gitlab_read_repository_file", description = "GitLab full file read test tool.")
        public Map<String, Object> readRepositoryFile() {
            gitLabReadFileCalls.incrementAndGet();
            return Map.of("status", "ok", "content", "class OrdersApi {}");
        }
    }
}
