package pl.mkn.incidenttracker.analysis.ai.copilot.tools.context;

import com.github.copilot.sdk.json.ToolInvocation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CopilotToolContextFactory {

    public ToolContext buildToolContext(CopilotToolSessionContext sessionContext, ToolInvocation invocation) {
        var context = new LinkedHashMap<String, Object>();

        if (sessionContext != null) {
            sessionContext.hiddenContext().forEach((key, value) -> putIfNotBlank(context, key, value));
        }

        if (invocation != null) {
            putIfNotBlank(context, AgentToolContextKeys.ACTUAL_COPILOT_SESSION_ID, invocation.getSessionId());
            putIfNotBlank(context, AgentToolContextKeys.TOOL_CALL_ID, invocation.getToolCallId());
            putIfNotBlank(context, AgentToolContextKeys.TOOL_NAME, invocation.getToolName());
        }

        return new ToolContext(context);
    }

    private void putIfNotBlank(Map<String, Object> context, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        if (value instanceof String stringValue) {
            if (!StringUtils.hasText(stringValue)) {
                return;
            }
            context.put(key, stringValue);
            return;
        }
        context.put(key, value);
    }
}
