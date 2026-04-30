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
            putIfNotBlank(context, AgentToolContextKeys.ANALYSIS_RUN_ID, sessionContext.analysisRunId());
            putIfNotBlank(context, AgentToolContextKeys.COPILOT_SESSION_ID, sessionContext.copilotSessionId());
            putIfNotBlank(context, AgentToolContextKeys.CORRELATION_ID, sessionContext.correlationId());
            putIfNotBlank(context, AgentToolContextKeys.ENVIRONMENT, sessionContext.environment());
            putIfNotBlank(context, AgentToolContextKeys.GITLAB_BRANCH, sessionContext.gitLabBranch());
            putIfNotBlank(context, AgentToolContextKeys.GITLAB_GROUP, sessionContext.gitLabGroup());
        }

        if (invocation != null) {
            putIfNotBlank(context, AgentToolContextKeys.ACTUAL_COPILOT_SESSION_ID, invocation.getSessionId());
            putIfNotBlank(context, AgentToolContextKeys.TOOL_CALL_ID, invocation.getToolCallId());
            putIfNotBlank(context, AgentToolContextKeys.TOOL_NAME, invocation.getToolName());
        }

        return new ToolContext(context);
    }

    private void putIfNotBlank(Map<String, Object> context, String key, String value) {
        if (StringUtils.hasText(value)) {
            context.put(key, value);
        }
    }
}
