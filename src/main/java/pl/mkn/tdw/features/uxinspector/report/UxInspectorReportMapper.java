package pl.mkn.tdw.features.uxinspector.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class UxInspectorReportMapper {
    public UxInspectorReportMapping map(AnalysisReport report, UxInspectorCapture capture,
                                        UxInspectorTargetContext context, AnalysisAiUsage usage) {
        if (report == null) return failed("UX Inspector session did not save an AnalysisReport through report tools.");
        if (report.sections().size() != 1 || !UxInspectorReportFactory.SECTION_ID.equals(report.sections().get(0).id())) {
            return failed("UX Inspector report must contain exactly one answer section.");
        }
        var sourceSection = report.sections().get(0);
        if (!StringUtils.hasText(sourceSection.markdown()) || looksLikeJson(sourceSection.markdown())) {
            return failed("UX Inspector answer section is empty or contains a JSON payload instead of Markdown.");
        }
        if (!StringUtils.hasText(report.header()) || !StringUtils.hasText(report.markdownSummary())) {
            return failed("UX Inspector report thesis was not saved through report_update_header.");
        }
        var sectionMeta = sourceSection.meta() != null ? sourceSection.meta() : AnalysisReportMeta.empty();
        var reportMeta = report.meta() != null ? report.meta() : AnalysisReportMeta.empty();
        var gaps = new LinkedHashSet<String>();
        gaps.addAll(sectionMeta.gaps());
        gaps.addAll(reportMeta.gaps());
        var limits = new LinkedHashSet<String>();
        limits.addAll(sectionMeta.visibilityLimits());
        limits.addAll(reportMeta.visibilityLimits());
        limits.addAll(gaps);
        limits.addAll(context.limitations());
        var openQuestions = new LinkedHashSet<String>();
        openQuestions.addAll(sectionMeta.openQuestions());
        openQuestions.addAll(reportMeta.openQuestions());
        var confidence = confidence(reportMeta.confidence(), sectionMeta.confidence());
        var warnings = new LinkedHashSet<String>();
        warnings.addAll(sectionMeta.warnings());
        warnings.addAll(reportMeta.warnings());
        var canonicalSectionMeta = new AnalysisReportMeta(
                List.of(), List.of(), List.of(), List.of(), null, List.of());
        var canonicalReportMeta = new AnalysisReportMeta(
                List.of(),
                distinct(sectionMeta.visibilityLimits(), reportMeta.visibilityLimits()),
                List.copyOf(openQuestions),
                List.copyOf(gaps),
                confidence,
                List.copyOf(warnings)
        );
        var safeSection = new AnalysisReportSection(UxInspectorReportFactory.SECTION_ID, "Odpowiedz", 1,
                sourceSection.markdown().trim(), canonicalSectionMeta);
        var safeReport = new AnalysisReport(report.reportId(), report.header().trim(), context.view().label(),
                report.markdownSummary().trim(), List.of(safeSection), canonicalReportMeta);
        var targetLabel = firstText(capture.target().accessibleName(), capture.target().text(), capture.target().tag());
        var result = new UxInspectorResultResponse(capture.captureId(), targetLabel, context.view(),
                context.sourceRevision(), context.status(), safeReport.markdownSummary(), safeSection.markdown(),
                confidence, List.of(), List.copyOf(limits), List.copyOf(openQuestions), usage);
        var complete = limits.isEmpty()
                && context.status() == pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus.RESOLVED;
        return new UxInspectorReportMapping(result, safeReport, complete, List.copyOf(limits));
    }

    private List<String> distinct(List<String> sectionValues, List<String> reportValues) {
        var values = new LinkedHashSet<String>();
        values.addAll(sectionValues);
        values.addAll(reportValues);
        return List.copyOf(values);
    }


    private UxInspectorReportMapping failed(String message) {
        return new UxInspectorReportMapping(null, null, false, List.of(message));
    }
    private boolean looksLikeJson(String value) {
        var trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```json");
    }
    private String confidence(String report, String section) {
        var value = StringUtils.hasText(report) ? report : section;
        return normalizeConfidence(value);
    }
    private String normalizeConfidence(String value) {
        return switch (StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "low") {
            case "high", "confirmed" -> "high";
            case "medium", "inferred" -> "medium";
            default -> "low";
        };
    }
    private String firstText(String... values) {
        for (var value : values) if (StringUtils.hasText(value)) return value.trim();
        return "selected element";
    }
}
