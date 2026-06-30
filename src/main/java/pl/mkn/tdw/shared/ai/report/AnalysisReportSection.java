package pl.mkn.tdw.shared.ai.report;

public record AnalysisReportSection(
        String id,
        String title,
        Integer order,
        String markdown,
        AnalysisReportMeta meta
) {

    public AnalysisReportSection {
        meta = meta != null ? meta : AnalysisReportMeta.empty();
    }
}
