package pl.mkn.tdw.shared.ai.report;

import java.util.List;

public record AnalysisReportEditRequest(
        String expectedRevisionSha256,
        String markdownSummary,
        List<AnalysisReportSectionEdit> sections
) {
    public AnalysisReportEditRequest {
        sections = sections != null ? List.copyOf(sections) : List.of();
    }
}
