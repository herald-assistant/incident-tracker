package pl.mkn.tdw.aiplatform.copilot.tools.report;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;

public record CopilotReportToolResult(
        String status,
        String message,
        String reportId,
        String reportFeature,
        AnalysisReport report,
        List<String> updatedSectionIds,
        List<String> allowedReportSectionIds
) {

    public CopilotReportToolResult {
        updatedSectionIds = updatedSectionIds != null ? List.copyOf(updatedSectionIds) : List.of();
        allowedReportSectionIds = allowedReportSectionIds != null ? List.copyOf(allowedReportSectionIds) : List.of();
    }
}
