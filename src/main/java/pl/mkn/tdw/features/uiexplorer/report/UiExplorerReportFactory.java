package pl.mkn.tdw.features.uiexplorer.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

@Component
public class UiExplorerReportFactory {

    public AnalysisReport createInitialReport(
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            CopilotToolSessionContext toolSessionContext
    ) {
        var sections = request.resolvedSectionModes().stream()
                .filter(assignment -> assignment.mode() != UiExplorerSectionMode.OFF)
                .map(assignment -> new AnalysisReportSection(
                        assignment.sectionId().name(),
                        assignment.sectionId().label(),
                        assignment.sectionId().ordinal(),
                        "",
                        AnalysisReportMeta.empty()
                ))
                .toList();
        return new AnalysisReport(
                reportId(toolSessionContext),
                screenPath(context),
                componentLabel(context),
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
        throw new IllegalStateException("UI Explorer initial report requires hidden reportId.");
    }

    private String screenPath(UiExplorerScreenReachabilityContext context) {
        if (context != null && context.screen() != null && StringUtils.hasText(context.screen().routePattern())) {
            return context.screen().routePattern().trim();
        }
        if (context != null && context.screen() != null && StringUtils.hasText(context.screen().screenId())) {
            return context.screen().screenId().trim();
        }
        return "screen";
    }

    private String componentLabel(UiExplorerScreenReachabilityContext context) {
        if (context != null && context.screen() != null && StringUtils.hasText(context.screen().label())) {
            return context.screen().label().trim();
        }
        return "Component unavailable";
    }
}
