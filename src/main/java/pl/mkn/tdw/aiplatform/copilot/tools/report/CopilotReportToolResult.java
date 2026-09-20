package pl.mkn.tdw.aiplatform.copilot.tools.report;

import java.util.List;

public record CopilotReportToolResult(
        String status,
        String message,
        String reportId,
        String reportFeature,
        CopilotReportManifest manifest,
        List<String> updatedSectionIds,
        List<String> allowedReportSectionIds
) {

    public CopilotReportToolResult {
        updatedSectionIds = updatedSectionIds != null ? List.copyOf(updatedSectionIds) : List.of();
        allowedReportSectionIds = allowedReportSectionIds != null ? List.copyOf(allowedReportSectionIds) : List.of();
    }
}
