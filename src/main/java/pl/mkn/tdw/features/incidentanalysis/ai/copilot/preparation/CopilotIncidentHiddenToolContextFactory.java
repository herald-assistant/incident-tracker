package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CopilotIncidentHiddenToolContextFactory {

    private static final String REPORT_FEATURE = "incident-analysis";

    public Map<String, Object> fromInitialRequest(InitialAnalysisRequest request) {
        if (request == null) {
            return Map.of();
        }

        var context = fromIncidentScope(
                request.correlationId(),
                request.environment()
        );
        putReportScope(context);
        return context;
    }

    public Map<String, Object> fromChatRequest(AnalysisAiChatRequest request) {
        if (request == null) {
            return Map.of();
        }

        return fromIncidentScope(
                request.correlationId(),
                request.environment()
        );
    }

    private Map<String, Object> fromIncidentScope(
            String correlationId,
            String environment
    ) {
        var context = new LinkedHashMap<String, Object>();
        putIfNotBlank(context, AgentToolContextKeys.CORRELATION_ID, correlationId);
        putIfNotBlank(context, AgentToolContextKeys.ENVIRONMENT, environment);
        return context;
    }

    private void putReportScope(Map<String, Object> context) {
        putIfNotBlank(context, AgentToolContextKeys.REPORT_ID, "report-" + UUID.randomUUID());
        putIfNotBlank(context, AgentToolContextKeys.REPORT_FEATURE, REPORT_FEATURE);
        context.put(
                AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS,
                CopilotIncidentReportSectionIds.INITIAL_ALLOWED_SECTION_IDS
        );
    }

    private void putIfNotBlank(Map<String, Object> context, String key, String value) {
        if (StringUtils.hasText(value)) {
            context.put(key, value);
        }
    }
}
