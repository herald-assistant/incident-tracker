package pl.mkn.tdw.features.flowexplorer.ai.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class FlowExplorerReportFactory {

    public AnalysisReport createInitialReport(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            CopilotToolSessionContext toolSessionContext
    ) {
        var reportId = reportId(toolSessionContext);
        var sections = new ArrayList<AnalysisReportSection>();
        sections.add(new AnalysisReportSection(
                FlowExplorerReportSectionIds.OVERVIEW,
                "Overview",
                0,
                "",
                AnalysisReportMeta.empty()
        ));

        var activeSections = FlowExplorerResultSectionModeResolver.activeOnly(
                request != null ? request.resolvedSectionModes() : null
        );
        for (var index = 0; index < activeSections.size(); index++) {
            var assignment = activeSections.get(index);
            if (assignment == null || assignment.id() == null) {
                continue;
            }
            sections.add(new AnalysisReportSection(
                    assignment.id().name(),
                    assignment.title(),
                    index + 1,
                    "",
                    AnalysisReportMeta.empty()
            ));
        }

        return new AnalysisReport(
                reportId,
                header(request, contextSnapshot),
                subHeader(request, contextSnapshot),
                "",
                sections,
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
        throw new IllegalStateException("Flow Explorer initial report requires hidden reportId.");
    }

    private String header(FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot) {
        return "Flow Explorer: " + endpointLabel(request, contextSnapshot);
    }

    private String subHeader(FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot) {
        var parts = new ArrayList<String>();
        add(parts, request != null ? request.systemId() : null);
        add(parts, contextSnapshot != null ? contextSnapshot.resolvedRef() : request != null ? request.branch() : null);
        add(parts, request != null && request.goal() != null ? request.goal().name() : null);
        return parts.isEmpty() ? "Initial analysis" : String.join(" | ", parts);
    }

    private String endpointLabel(FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot) {
        var method = contextSnapshot != null ? contextSnapshot.httpMethod() : request != null ? request.httpMethod() : null;
        var path = contextSnapshot != null ? contextSnapshot.endpointPath() : request != null ? request.endpointPath() : null;
        if (StringUtils.hasText(method) && StringUtils.hasText(path)) {
            return method.trim() + " " + path.trim();
        }
        var endpointId = contextSnapshot != null ? contextSnapshot.endpointId() : request != null ? request.endpointId() : null;
        return StringUtils.hasText(endpointId) ? endpointId.trim() : "selected endpoint";
    }

    private void add(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }
}
