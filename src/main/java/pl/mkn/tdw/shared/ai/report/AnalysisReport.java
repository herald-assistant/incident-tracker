package pl.mkn.tdw.shared.ai.report;

import java.util.List;

public record AnalysisReport(
        String reportId,
        String header,
        String subHeader,
        String markdownSummary,
        List<AnalysisReportSection> sections,
        AnalysisReportMeta meta
) {

    public AnalysisReport {
        sections = sections != null ? List.copyOf(sections) : List.of();
        meta = meta != null ? meta : AnalysisReportMeta.empty();
    }

    public boolean hasSections() {
        return !sections.isEmpty();
    }
}
