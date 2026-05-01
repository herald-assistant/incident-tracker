package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CopilotIncidentHiddenToolContextFactory {

    public Map<String, Object> fromInitialRequest(InitialAnalysisRequest request) {
        if (request == null) {
            return Map.of();
        }

        return fromIncidentScope(
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
    }

    public Map<String, Object> fromChatRequest(AnalysisAiChatRequest request) {
        if (request == null) {
            return Map.of();
        }

        return fromIncidentScope(
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
    }

    private Map<String, Object> fromIncidentScope(
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

    private void putIfNotBlank(Map<String, Object> context, String key, String value) {
        if (StringUtils.hasText(value)) {
            context.put(key, value);
        }
    }
}
