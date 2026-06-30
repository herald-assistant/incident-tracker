package pl.mkn.tdw.shared.ai.report;

import java.util.List;

public record AnalysisReportMeta(
        List<AnalysisReportReference> references,
        List<String> visibilityLimits,
        List<String> openQuestions,
        List<String> gaps,
        String confidence,
        List<String> warnings
) {

    public AnalysisReportMeta {
        references = references != null ? List.copyOf(references) : List.of();
        visibilityLimits = copyTextList(visibilityLimits);
        openQuestions = copyTextList(openQuestions);
        gaps = copyTextList(gaps);
        warnings = copyTextList(warnings);
    }

    public static AnalysisReportMeta empty() {
        return new AnalysisReportMeta(null, null, null, null, null, null);
    }

    private static List<String> copyTextList(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }
}
