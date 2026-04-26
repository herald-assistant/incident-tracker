package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolBudgetGuardTest {

    @Test
    void shouldWarnButAllowInSoftMode() {
        var properties = properties(BudgetMode.SOFT);
        properties.setMaxTotalCalls(0);
        var metricsRegistry = metricsRegistry();
        var guard = guard(properties, metricsRegistry);
        var context = registerMetrics(metricsRegistry);

        var decision = guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        assertFalse(decision.denied());
        assertTrue(decision.softLimitExceeded());
        assertEquals(1, metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow().budgetSoftLimitExceededCount());
    }

    @Test
    void shouldBlockInHardModeAfterTotalCallBudgetIsExceeded() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxTotalCalls(1);
        var metricsRegistry = metricsRegistry();
        var registry = new CopilotToolBudgetRegistry(properties);
        var guard = new CopilotToolBudgetGuard(registry, metricsRegistry);
        var context = registerMetrics(metricsRegistry);
        registry.registerSession(context.copilotSessionId());

        assertFalse(guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}").denied());
        guard.afterInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        var denied = guard.beforeInvocation(context.copilotSessionId(), "gitlab_find_flow_context", "{}");

        assertTrue(denied.denied());
        assertTrue(denied.reason().contains("total tool call budget exceeded"));
        assertEquals(1, metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow().budgetDeniedToolCalls());
    }

    @Test
    void shouldApplyGitLabReadFileLimit() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxGitlabReadFileCalls(0);
        var guard = guard(properties, metricsRegistry());

        var decision = guard.beforeInvocation("analysis-run-1", "gitlab_read_repository_file", "{}");

        assertTrue(decision.denied());
        assertTrue(decision.reason().contains("GitLab full file read budget exceeded"));
    }

    @Test
    void shouldApplyDbRawSqlLimitAndCountAttempts() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxDbRawSqlCalls(0);
        var metricsRegistry = metricsRegistry();
        var guard = guard(properties, metricsRegistry);
        var context = registerMetrics(metricsRegistry);

        var decision = guard.beforeInvocation(context.copilotSessionId(), "db_execute_readonly_sql", "{}");

        assertTrue(decision.denied());
        assertTrue(decision.reason().contains("Database raw SQL tool call budget exceeded"));
        var metrics = metricsRegistry.snapshot(context.copilotSessionId()).orElseThrow();
        assertEquals(1, metrics.budgetRawSqlAttempts());
        assertEquals(1, metrics.budgetDeniedToolCalls());
    }

    @Test
    void shouldDenyNextCallAfterReturnedCharacterLimitIsExceeded() {
        var properties = properties(BudgetMode.HARD);
        properties.setMaxGitlabReturnedCharacters(5);
        var guard = guard(properties, metricsRegistry());

        assertFalse(guard.beforeInvocation("analysis-run-1", "gitlab_find_flow_context", "{}").denied());
        var postCall = guard.afterInvocation("analysis-run-1", "gitlab_find_flow_context", "123456");
        assertTrue(postCall.softLimitExceeded());

        var denied = guard.beforeInvocation("analysis-run-1", "gitlab_find_flow_context", "{}");

        assertTrue(denied.denied());
        assertTrue(denied.reason().contains("GitLab returned character budget already exhausted"));
    }

    private CopilotToolBudgetGuard guard(
            CopilotToolBudgetProperties properties,
            CopilotSessionMetricsRegistry metricsRegistry
    ) {
        var registry = new CopilotToolBudgetRegistry(properties);
        registry.registerSession("analysis-run-1");
        return new CopilotToolBudgetGuard(registry, metricsRegistry);
    }

    private CopilotToolBudgetProperties properties(BudgetMode mode) {
        var properties = new CopilotToolBudgetProperties();
        properties.setMode(mode);
        return properties;
    }

    private CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }

    private CopilotToolSessionContext registerMetrics(CopilotSessionMetricsRegistry metricsRegistry) {
        var context = new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "sample/runtime"
        );
        metricsRegistry.recordPreparation(
                context,
                new AnalysisAiAnalysisRequest("corr-123", "zt01", "main", "sample/runtime", List.of()),
                List.of(),
                "prompt",
                1L
        );
        return context;
    }
}
