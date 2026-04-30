package pl.mkn.incidenttracker.analysis.ai.copilot.tools.context;

import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CopilotToolSessionContext(
        String analysisRunId,
        String copilotSessionId,
        Map<String, Object> hiddenContext
) {

    public CopilotToolSessionContext {
        hiddenContext = normalizeHiddenContext(analysisRunId, copilotSessionId, hiddenContext);
    }

    public CopilotToolSessionContext(
            String analysisRunId,
            String copilotSessionId,
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup
    ) {
        this(analysisRunId, copilotSessionId, incidentHiddenContext(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup
        ));
    }

    public String correlationId() {
        return stringValue(AgentToolContextKeys.CORRELATION_ID);
    }

    public String environment() {
        return stringValue(AgentToolContextKeys.ENVIRONMENT);
    }

    public String gitLabBranch() {
        return stringValue(AgentToolContextKeys.GITLAB_BRANCH);
    }

    public String gitLabGroup() {
        return stringValue(AgentToolContextKeys.GITLAB_GROUP);
    }

    private String stringValue(String key) {
        var value = hiddenContext.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    private static Map<String, Object> incidentHiddenContext(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup
    ) {
        var context = new LinkedHashMap<String, Object>();
        putIfNotBlank(context, AgentToolContextKeys.CORRELATION_ID, correlationId);
        putIfNotBlank(context, AgentToolContextKeys.ENVIRONMENT, environment);
        putIfNotBlank(context, AgentToolContextKeys.GITLAB_BRANCH, gitLabBranch);
        putIfNotBlank(context, AgentToolContextKeys.GITLAB_GROUP, gitLabGroup);
        return context;
    }

    private static Map<String, Object> normalizeHiddenContext(
            String analysisRunId,
            String copilotSessionId,
            Map<String, Object> hiddenContext
    ) {
        var normalized = new LinkedHashMap<String, Object>();
        if (hiddenContext != null) {
            hiddenContext.forEach((key, value) -> putIfNotBlank(normalized, key, value));
        }
        putIfNotBlank(normalized, AgentToolContextKeys.ANALYSIS_RUN_ID, analysisRunId);
        putIfNotBlank(normalized, AgentToolContextKeys.COPILOT_SESSION_ID, copilotSessionId);
        return Collections.unmodifiableMap(normalized);
    }

    private static void putIfNotBlank(Map<String, Object> context, String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        context.put(key, value);
    }
}
