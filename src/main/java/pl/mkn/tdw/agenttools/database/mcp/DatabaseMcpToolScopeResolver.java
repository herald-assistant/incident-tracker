package pl.mkn.tdw.agenttools.database.mcp;

import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.integrations.database.DatabaseCapabilityDtos.DbCapabilityScope;

import java.util.Map;

import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.ACTUAL_COPILOT_SESSION_ID;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.ANALYSIS_RUN_ID;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.COPILOT_SESSION_ID;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.CORRELATION_ID;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.ENVIRONMENT;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.TOOL_CALL_ID;
import static pl.mkn.tdw.agenttools.context.AgentToolContextKeys.TOOL_NAME;

final class DatabaseMcpToolScopeResolver {

    private DatabaseMcpToolScopeResolver() {
    }

    static DbCapabilityScope from(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("Missing Copilot tool context; database tools require session-bound scope.");
        }

        var context = toolContext.getContext();

        return new DbCapabilityScope(
                required(
                        context,
                        CORRELATION_ID,
                        "Missing correlationId in Copilot tool context; database tools require current incident correlationId."
                ),
                required(
                        context,
                        ENVIRONMENT,
                        "Missing environment in Copilot tool context; database tools require session-bound environment."
                ),
                optional(context, ANALYSIS_RUN_ID),
                firstNonBlank(optional(context, ACTUAL_COPILOT_SESSION_ID), optional(context, COPILOT_SESSION_ID)),
                optional(context, TOOL_CALL_ID),
                optional(context, TOOL_NAME)
        );
    }

    private static String required(Map<String, Object> context, String key, String message) {
        var value = context.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.toString();
    }

    private static String optional(Map<String, Object> context, String key) {
        var value = context.get(key);
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
