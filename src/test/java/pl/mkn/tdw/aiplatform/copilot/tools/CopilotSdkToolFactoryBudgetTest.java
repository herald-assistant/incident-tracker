package pl.mkn.tdw.aiplatform.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.BudgetMode;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkToolFactoryBudgetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnControlledDeniedResultInHardModeWithoutInvokingCallback() {
        var tools = new BudgetTestTools();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.HARD);
        properties.setMaxDbRawSqlCalls(0);
        var budgetRegistry = new CopilotToolBudgetRegistry(properties);
        var factory = factory(tools, budgetRegistry);
        var context = registerSession(budgetRegistry);
        var tool = toolByName(factory, context, "db_execute_readonly_sql");

        var result = invoke(tool, context, "tool-db-1");

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;
        assertEquals("denied_by_tool_budget", payload.get("status"));
        assertEquals("db_execute_readonly_sql", payload.get("toolName"));
        assertTrue(payload.get("reason").toString().contains("Database raw SQL tool call budget exceeded"));
        assertEquals(0, tools.rawSqlCalls.get());

        var snapshot = budgetRegistry.state(context.copilotSessionId()).orElseThrow().snapshot();
        assertEquals(1, snapshot.rawSqlAttempts());
        assertEquals(1, snapshot.deniedToolCalls());
        assertEquals(0, snapshot.dbRawSqlCalls());
    }

    @Test
    void shouldAllowToolCallInSoftModeAndRecordWarning() {
        var tools = new BudgetTestTools();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.SOFT);
        properties.setMaxTotalCalls(0);
        var budgetRegistry = new CopilotToolBudgetRegistry(properties);
        var factory = factory(tools, budgetRegistry);
        var context = registerSession(budgetRegistry);
        var tool = toolByName(factory, context, "gitlab_read_repository_file");

        var result = invoke(tool, context, "tool-gitlab-1");

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;
        assertEquals("ok", payload.get("status"));
        assertEquals(1, tools.gitLabReadFileCalls.get());
        var snapshot = budgetRegistry.state(context.copilotSessionId()).orElseThrow().snapshot();
        assertTrue(snapshot.softLimitExceededCount() > 0);
        assertEquals(1, snapshot.gitLabReadFileCalls());
    }

    @Test
    void shouldTreatEndpointUseCaseContextAsGitLabSearchBudget() {
        var tools = new BudgetTestTools();
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(BudgetMode.HARD);
        properties.setMaxGitlabSearchCalls(0);
        var budgetRegistry = new CopilotToolBudgetRegistry(properties);
        var factory = factory(tools, budgetRegistry);
        var context = registerSession(budgetRegistry);
        var tool = toolByName(factory, context, "gitlab_build_endpoint_use_case_context");

        var result = invoke(tool, context, "tool-gitlab-context-1");

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) result;
        assertEquals("denied_by_tool_budget", payload.get("status"));
        assertTrue(payload.get("reason").toString().contains("GitLab search tool call budget exceeded"));
        assertEquals(0, tools.gitLabEndpointContextCalls.get());

        var snapshot = budgetRegistry.state(context.copilotSessionId()).orElseThrow().snapshot();
        assertEquals(1, snapshot.deniedToolCalls());
        assertEquals(0, snapshot.gitLabSearchCalls());
    }

    private CopilotSdkToolFactory factory(
            BudgetTestTools tools,
            CopilotToolBudgetRegistry budgetRegistry
    ) {
        return toolFactory(
                List.of(MethodToolCallbackProvider.builder().toolObjects(tools).build()),
                objectMapper,
                toolEvidenceSessionStore(objectMapper),
                new CopilotToolBudgetPolicy(
                        budgetRegistry
                )
        );
    }

    private CopilotToolSessionContext registerSession(CopilotToolBudgetRegistry budgetRegistry) {
        var context = new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "CRM/runtime"
        );
        budgetRegistry.registerSession(context.copilotSessionId());
        return context;
    }

    private ToolDefinition toolByName(
            CopilotSdkToolFactory factory,
            CopilotToolSessionContext context,
            String toolName
    ) {
        return factory.createToolDefinitions(context, CopilotToolDescriptionContext.empty()).stream()
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
        private final AtomicInteger gitLabEndpointContextCalls = new AtomicInteger();

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

        @Tool(name = "gitlab_build_endpoint_use_case_context", description = "GitLab endpoint use case context test tool.")
        public Map<String, Object> buildEndpointUseCaseContext() {
            gitLabEndpointContextCalls.incrementAndGet();
            return Map.of("status", "ok");
        }
    }
}
