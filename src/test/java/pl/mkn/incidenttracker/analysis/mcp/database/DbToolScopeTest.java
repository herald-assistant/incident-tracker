package pl.mkn.incidenttracker.analysis.mcp.database;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pl.mkn.incidenttracker.agenttools.database.DatabaseToolDtos.DbToolScope;

class DbToolScopeTest {

    @Test
    void shouldCreateScopeFromToolContext() {
        var scope = DbToolScope.from(toolContext("zt01", "corr-123"));

        assertEquals("corr-123", scope.correlationId());
        assertEquals("zt01", scope.environment());
        assertEquals("run-1", scope.analysisRunId());
        assertEquals("analysis-run-1", scope.copilotSessionId());
        assertEquals("tool-call-1", scope.toolCallId());
        assertEquals("db_find_tables", scope.toolName());
    }

    @Test
    void shouldFailWhenEnvironmentIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> DbToolScope.from(toolContext(null, "corr-123")));

        assertEquals(
                "Missing environment in Copilot tool context; database tools require session-bound environment.",
                exception.getMessage()
        );
    }

    @Test
    void shouldFailWhenCorrelationIdIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> DbToolScope.from(toolContext("zt01", null)));

        assertEquals(
                "Missing correlationId in Copilot tool context; database tools require current incident correlationId.",
                exception.getMessage()
        );
    }

    private ToolContext toolContext(String environment, String correlationId) {
        var context = new LinkedHashMap<String, Object>();
        if (environment != null) {
            context.put(AgentToolContextKeys.ENVIRONMENT, environment);
        }
        if (correlationId != null) {
            context.put(AgentToolContextKeys.CORRELATION_ID, correlationId);
        }
        context.put(AgentToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(AgentToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(AgentToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(AgentToolContextKeys.TOOL_NAME, "db_find_tables");
        return new ToolContext(context);
    }
}
