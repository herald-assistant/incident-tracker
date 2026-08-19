package pl.mkn.tdw.aiplatform.copilot.tools.policy.budget;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyResult;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolBudgetPolicyTest {

    @Test
    void shouldWarnButAllowInSoftMode() {
        var properties = properties(BudgetMode.SOFT);
        properties.setMaxTotalCalls(0);
        var registry = registerBudget(properties);
        var guard = new CopilotToolBudgetPolicy(registry);
        var context = sessionContext();

        var decision = guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        assertFalse(decision.denied());
        assertTrue(decision.softLimitExceeded());
        assertEquals(1, registry.state(context.copilotSessionId()).orElseThrow().snapshot().softLimitExceededCount());
    }

    @Test
    void shouldBlockInHardModeAfterTotalCallBudgetIsExceeded() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxTotalCalls(1);
        var registry = new CopilotToolBudgetRegistry(properties);
        var guard = new CopilotToolBudgetPolicy(registry);
        var context = sessionContext();
        registry.registerSession(context.copilotSessionId());

        assertFalse(guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}").denied());
        guard.afterInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        var denied = guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        assertTrue(denied.denied());
        assertTrue(denied.reason().contains("total tool call budget exceeded"));
        assertEquals(1, registry.state(context.copilotSessionId()).orElseThrow().snapshot().deniedToolCalls());
    }

    @Test
    void shouldApplyGitLabReadFileLimit() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxGitlabReadFileCalls(0);
        var guard = guard(properties);

        var decision = guard.beforeInvocation("analysis-run-1", "gitlab_read_repository_files_by_path", "{}");

        assertTrue(decision.denied());
        assertTrue(decision.reason().contains("GitLab full file read budget exceeded"));
    }

    @Test
    void shouldApplyDbRawSqlLimitAndCountAttempts() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxDbRawSqlCalls(0);
        var registry = registerBudget(properties);
        var guard = new CopilotToolBudgetPolicy(registry);
        var context = sessionContext();

        var decision = guard.beforeInvocation(context.copilotSessionId(), "db_execute_readonly_sql", "{}");

        assertTrue(decision.denied());
        assertTrue(decision.reason().contains("Database raw SQL tool call budget exceeded"));
        var snapshot = registry.state(context.copilotSessionId()).orElseThrow().snapshot();
        assertEquals(1, snapshot.rawSqlAttempts());
        assertEquals(1, snapshot.deniedToolCalls());
    }

    @Test
    void shouldDenyNextCallAfterReturnedCharacterLimitIsExceeded() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxGitlabReturnedCharacters(5);
        var guard = guard(properties);

        assertFalse(guard.beforeInvocation("analysis-run-1", "gitlab_find_flow_context", "{}").denied());
        var postCall = guard.afterInvocation("analysis-run-1", "gitlab_find_flow_context", "123456");
        assertTrue(postCall.softLimitExceeded());

        var denied = guard.beforeInvocation("analysis-run-1", "gitlab_find_flow_context", "{}");

        assertTrue(denied.denied());
        assertTrue(denied.reason().contains("GitLab returned character budget already exhausted"));
    }

    @Test
    void shouldApplyOperationalContextCallAndReturnedCharacterLimits() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxOperationalContextCalls(1);
        properties.setMaxOperationalContextReturnedCharacters(5);
        var registry = registerBudget(properties);
        var guard = new CopilotToolBudgetPolicy(registry);

        assertFalse(guard.beforeInvocation("analysis-run-1", "opctx_search", "{}").denied());
        var postCall = guard.afterInvocation("analysis-run-1", "opctx_search", "123456");
        assertTrue(postCall.softLimitExceeded());

        var denied = guard.beforeInvocation("analysis-run-1", "opctx_get_entity", "{}");

        assertTrue(denied.denied());
        assertTrue(denied.reason().contains("Operational context tool call budget exceeded"));
        var snapshot = registry.state("analysis-run-1").orElseThrow().snapshot();
        assertEquals(1, snapshot.operationalContextCalls());
        assertEquals(6, snapshot.operationalContextReturnedCharacters());
    }

    @Test
    void shouldNotCountToolFeedbackAgainstExplorationBudget() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxTotalCalls(0);
        var registry = registerBudget(properties);
        var guard = new CopilotToolBudgetPolicy(registry);

        var before = guard.beforeInvocation("analysis-run-1", "record_tool_feedback", "{}");
        var after = guard.afterInvocation("analysis-run-1", "record_tool_feedback", "{\"status\":\"recorded\"}");

        assertFalse(before.denied());
        assertFalse(after.denied());
        var snapshot = registry.state("analysis-run-1").orElseThrow().snapshot();
        assertEquals(0, snapshot.totalCalls());
        assertEquals(0, snapshot.deniedToolCalls());
        assertEquals(0, snapshot.softLimitExceededCount());
    }

    @Test
    void shouldBypassCallAndCharacterBudgetsForGoalDrivenUiExplorerSession() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxTotalCalls(0);
        properties.setMaxGitlabReturnedCharacters(0);
        var registry = new CopilotToolBudgetRegistry(properties);
        registry.registerSession("ui-explorer-session");
        var guard = new CopilotToolBudgetPolicy(registry);
        var context = new CopilotToolSessionContext(
                "ui-explorer-run",
                "ui-explorer-session",
                Map.of(
                        AgentToolContextKeys.TOOL_BUDGET_POLICY,
                        AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN
                )
        );
        var request = new CopilotToolInvocationPolicyRequest(
                context,
                context.copilotSessionId(),
                "tool-call-1",
                "gitlab_expand_frontend_use_case_context",
                "{\"frontierId\":\"crm-preferences\"}"
        );
        var result = new CopilotToolInvocationPolicyResult(
                context,
                context.copilotSessionId(),
                "tool-call-1",
                "gitlab_expand_frontend_use_case_context",
                "{\"frontierId\":\"crm-preferences\"}",
                "1234567890"
        );

        assertDoesNotThrow(() -> guard.beforeInvocation(request));
        assertDoesNotThrow(() -> guard.afterInvocation(result));
        assertEquals(0, registry.state(context.copilotSessionId()).orElseThrow().snapshot().totalCalls());

        var boundedContext = new CopilotToolSessionContext(
                "bounded-run",
                "ui-explorer-session",
                Map.of()
        );
        assertThrows(
                RuntimeException.class,
                () -> guard.beforeInvocation(new CopilotToolInvocationPolicyRequest(
                        boundedContext,
                        boundedContext.copilotSessionId(),
                        "tool-call-2",
                        "gitlab_find_flow_context",
                        "{}"
                ))
        );
    }

    private CopilotToolBudgetPolicy guard(CopilotToolBudgetProperties properties) {
        return new CopilotToolBudgetPolicy(registerBudget(properties));
    }

    private CopilotToolBudgetRegistry registerBudget(CopilotToolBudgetProperties properties) {
        var registry = new CopilotToolBudgetRegistry(properties);
        registry.registerSession(sessionContext().copilotSessionId());
        return registry;
    }

    private CopilotToolBudgetProperties properties(BudgetMode mode) {
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(mode);
        return properties;
    }

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "CRM/runtime"
        );
    }
}
