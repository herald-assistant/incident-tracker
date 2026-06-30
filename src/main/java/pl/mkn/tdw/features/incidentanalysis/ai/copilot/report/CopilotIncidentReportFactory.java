package pl.mkn.tdw.features.incidentanalysis.ai.copilot.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentReportSectionIds;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

@Component
public class CopilotIncidentReportFactory {

    public AnalysisReport createInitialReport(
            InitialAnalysisRequest request,
            CopilotToolSessionContext toolSessionContext
    ) {
        return new AnalysisReport(
                reportId(toolSessionContext),
                "Incident analysis pending",
                subHeader(request),
                "",
                List.of(
                        new AnalysisReportSection(
                                CopilotIncidentReportSectionIds.FUNCTIONAL_ANALYSIS,
                                "Functional analysis",
                                1,
                                "",
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                CopilotIncidentReportSectionIds.TECHNICAL_HANDOFF,
                                "Technical handoff",
                                2,
                                "",
                                AnalysisReportMeta.empty()
                        )
                ),
                AnalysisReportMeta.empty()
        );
    }

    private String reportId(CopilotToolSessionContext toolSessionContext) {
        var value = toolSessionContext != null
                ? toolSessionContext.hiddenContext().get(AgentToolContextKeys.REPORT_ID)
                : null;
        if (value instanceof String reportId && StringUtils.hasText(reportId)) {
            return reportId.trim();
        }
        throw new IllegalStateException("Incident initial report requires hidden reportId.");
    }

    private String subHeader(InitialAnalysisRequest request) {
        if (request == null) {
            return "Initial incident analysis";
        }
        var parts = new java.util.ArrayList<String>();
        add(parts, request.correlationId());
        add(parts, request.environment());
        add(parts, request.gitLabBranch());
        return parts.isEmpty() ? "Initial incident analysis" : String.join(" | ", parts);
    }

    private void add(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }
}
