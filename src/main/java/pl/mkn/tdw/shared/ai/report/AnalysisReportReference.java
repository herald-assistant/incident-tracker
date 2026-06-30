package pl.mkn.tdw.shared.ai.report;

public record AnalysisReportReference(
        String type,
        String label,
        String target,
        String description
) {
}
