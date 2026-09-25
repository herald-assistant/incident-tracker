package pl.mkn.tdw.shared.ai.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnalysisReport(
        String reportId,
        String header,
        String subHeader,
        String markdownSummary,
        List<AnalysisReportSection> sections,
        AnalysisReportMeta meta,
        AnalysisReportManualEdit manualEdit
) {

    public AnalysisReport {
        sections = sections != null ? List.copyOf(sections) : List.of();
        meta = meta != null ? meta : AnalysisReportMeta.empty();
    }

    public AnalysisReport(String reportId, String header, String subHeader, String markdownSummary,
                          List<AnalysisReportSection> sections, AnalysisReportMeta meta) {
        this(reportId, header, subHeader, markdownSummary, sections, meta, null);
    }

    @JsonProperty(value = "revisionSha256", access = JsonProperty.Access.READ_ONLY)
    public String revisionSha256() {
        return AnalysisReportDigest.sha256(this);
    }

    public boolean hasSections() {
        return !sections.isEmpty();
    }
}
