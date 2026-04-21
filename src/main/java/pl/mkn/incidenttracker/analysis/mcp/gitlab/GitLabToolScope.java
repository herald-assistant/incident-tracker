package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.ACTUAL_COPILOT_SESSION_ID;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.ANALYSIS_RUN_ID;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.COPILOT_SESSION_ID;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.CORRELATION_ID;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.ENVIRONMENT;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.GITLAB_BRANCH;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.GITLAB_GROUP;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.TOOL_CALL_ID;
import static pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys.TOOL_NAME;

public record GitLabToolScope(
        String correlationId,
        String group,
        String branch,
        String environment,
        String analysisRunId,
        String copilotSessionId,
        String toolCallId,
        String toolName
) {

    public static GitLabToolScope from(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("Missing Copilot tool context; GitLab tools require session-bound scope.");
        }

        var context = toolContext.getContext();

        return new GitLabToolScope(
                required(
                        context,
                        CORRELATION_ID,
                        "Missing correlationId in Copilot tool context; GitLab tools require session-bound correlationId."
                ),
                required(
                        context,
                        GITLAB_GROUP,
                        "Missing gitLabGroup in Copilot tool context; GitLab tools require session-bound group."
                ),
                required(
                        context,
                        GITLAB_BRANCH,
                        "Missing gitLabBranch in Copilot tool context; GitLab tools require resolved session branch."
                ),
                optional(context, ENVIRONMENT),
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
