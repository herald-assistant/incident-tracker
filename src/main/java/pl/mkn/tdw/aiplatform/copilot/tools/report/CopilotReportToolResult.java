package pl.mkn.tdw.aiplatform.copilot.tools.report;

import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

public record CopilotReportToolResult(
        String status,
        String message,
        String reportId,
        String reportFeature,
        CopilotReportManifest manifest,
        List<String> updatedSectionIds,
        List<String> allowedReportSectionIds,
        AnalysisReportSection section
) {

    public CopilotReportToolResult {
        updatedSectionIds = updatedSectionIds != null ? List.copyOf(updatedSectionIds) : List.of();
        allowedReportSectionIds = allowedReportSectionIds != null ? List.copyOf(allowedReportSectionIds) : List.of();
    }

    public CopilotReportToolResult(String status, String message, String reportId, String reportFeature,
                                   CopilotReportManifest manifest, List<String> updatedSectionIds,
                                   List<String> allowedReportSectionIds) {
        this(status, message, reportId, reportFeature, manifest, updatedSectionIds,
                allowedReportSectionIds, null);
    }
}
