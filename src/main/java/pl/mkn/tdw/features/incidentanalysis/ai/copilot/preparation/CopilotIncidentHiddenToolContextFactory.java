package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceView;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
                request.environment(),
                request.evidenceSections()
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
                request.environment(),
                request.evidenceSections()
        );
    }

    private Map<String, Object> fromIncidentScope(
            String correlationId,
            String environment,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        var context = new LinkedHashMap<String, Object>();
        putIfNotBlank(context, AgentToolContextKeys.CORRELATION_ID, correlationId);
        putIfNotBlank(context, AgentToolContextKeys.ENVIRONMENT, environment);
        putGitLabApplicationScope(context, evidenceSections);
        return context;
    }

    private void putGitLabApplicationScope(
            Map<String, Object> context,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        var applicationNames = new LinkedHashSet<String>();
        OperationalContextEvidenceView.from(evidenceSections).systems().stream()
                .filter(system -> !system.codeSearchScopeIds().isEmpty())
                .map(OperationalContextEvidenceView.SystemItem::systemId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(applicationNames::add);
        if (!applicationNames.isEmpty()) {
            context.put(
                    AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES,
                    List.copyOf(applicationNames)
            );
        }
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
