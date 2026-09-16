package pl.mkn.tdw.features.uxinspector.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UxInspectorReportMapper {
    private static final Pattern SOURCE_TARGET = Pattern.compile("^(?<path>[^#]+?)(?:#L(?<start>\\d+)(?:-L(?<end>\\d+))?)?$");

    public UxInspectorReportMapping map(AnalysisReport report, UxInspectorCapture capture,
                                        UxInspectorTargetContext context, Set<String> toolReadSourceRefs,
                                        AnalysisAiUsage usage) {
        var errors = new LinkedHashSet<String>();
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
        var allowedSourcePaths = allowedSourcePaths(context, toolReadSourceRefs);
        var sectionMeta = validateMeta(sourceSection.meta(), allowedSourcePaths, errors);
        var reportMeta = validateMeta(report.meta(), allowedSourcePaths, errors);
        if (!errors.isEmpty()) return new UxInspectorReportMapping(null, null, false, List.copyOf(errors));
        var references = new LinkedHashSet<AnalysisReportReference>();
        references.addAll(sectionMeta.references());
        references.addAll(reportMeta.references());
        var gaps = new LinkedHashSet<String>();
        gaps.addAll(sectionMeta.gaps());
        gaps.addAll(reportMeta.gaps());
        var limits = new LinkedHashSet<String>();
        limits.addAll(sectionMeta.visibilityLimits());
        limits.addAll(reportMeta.visibilityLimits());
        limits.addAll(gaps);
        if (references.isEmpty() && limits.isEmpty()) {
            return failed("UX Inspector answer has no verified source reference and no explicit evidence gap.");
        }
        limits.addAll(context.limitations());
        var openQuestions = new LinkedHashSet<String>();
        openQuestions.addAll(sectionMeta.openQuestions());
        openQuestions.addAll(reportMeta.openQuestions());
        var confidence = confidence(reportMeta.confidence(), sectionMeta.confidence(), !references.isEmpty());
        var safeSection = new AnalysisReportSection(UxInspectorReportFactory.SECTION_ID, "Odpowiedz", 1,
                sourceSection.markdown().trim(), sectionMeta);
        var safeReport = new AnalysisReport(report.reportId(), report.header().trim(), context.view().label(),
                report.markdownSummary().trim(), List.of(safeSection), reportMeta);
        var targetLabel = firstText(capture.target().accessibleName(), capture.target().text(), capture.target().tag());
        var result = new UxInspectorResultResponse(capture.captureId(), targetLabel, context.view(),
                context.sourceRevision(), context.status(), safeReport.markdownSummary(), safeSection.markdown(),
                confidence, List.copyOf(references), List.copyOf(limits), List.copyOf(openQuestions), usage);
        var complete = limits.isEmpty() && context.status() == pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus.RESOLVED;
        return new UxInspectorReportMapping(result, safeReport, complete, List.copyOf(limits));
    }

    private AnalysisReportMeta validateMeta(AnalysisReportMeta source, Set<String> allowedSourcePaths,
                                            LinkedHashSet<String> errors) {
        var meta = source != null ? source : AnalysisReportMeta.empty();
        var references = new ArrayList<AnalysisReportReference>();
        for (var reference : meta.references()) {
            var safe = reference(reference, allowedSourcePaths);
            if (safe == null) errors.add("UX Inspector report contains an out-of-scope or invalid source reference.");
            else references.add(safe);
        }
        return new AnalysisReportMeta(references, meta.visibilityLimits(), meta.openQuestions(), meta.gaps(),
                StringUtils.hasText(meta.confidence()) ? normalizeConfidence(meta.confidence()) : null, meta.warnings());
    }

    private AnalysisReportReference reference(AnalysisReportReference value, Set<String> allowedSourcePaths) {
        if (value == null || !StringUtils.hasText(value.target())) return null;
        var matcher = SOURCE_TARGET.matcher(value.target().trim().replace('\\', '/'));
        if (!matcher.matches()) return null;
        var path = matcher.group("path").replaceAll("^/+", "");
        if (!allowedSourcePaths.contains(path)) return null;
        var start = positive(matcher.group("start"));
        var end = positive(matcher.group("end"));
        if (matcher.group("start") != null && start == null || matcher.group("end") != null
                && (start == null || end == null || end < start)) return null;
        var target = path + (start != null ? "#L" + start + (end != null && !end.equals(start) ? "-L" + end : "") : "");
        return new AnalysisReportReference("source", StringUtils.hasText(value.label()) ? value.label().trim() : path,
                target, StringUtils.hasText(value.description()) ? value.description().trim() : "Pinned frontend source");
    }

    private Set<String> allowedSourcePaths(UxInspectorTargetContext context, Set<String> toolReadSourceRefs) {
        var paths = new LinkedHashSet<>(context.allowedSourcePaths());
        var prefix = "gitlab:" + context.sourceScope().group() + "/" + context.sourceScope().projectName()
                + "@" + context.sourceRevision().revision() + ":";
        for (var sourceRef : toolReadSourceRefs != null ? toolReadSourceRefs : Set.<String>of()) {
            if (!StringUtils.hasText(sourceRef) || !sourceRef.startsWith(prefix)) continue;
            var path = sourceRef.substring(prefix.length());
            if (GitLabVerifiedRepositoryFileReader.isSafePath(path, false)) paths.add(path);
        }
        return Set.copyOf(paths);
    }

    private UxInspectorReportMapping failed(String message) {
        return new UxInspectorReportMapping(null, null, false, List.of(message));
    }
    private boolean looksLikeJson(String value) {
        var trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```json");
    }
    private String confidence(String report, String section, boolean referenced) {
        var value = StringUtils.hasText(report) ? report : section;
        var normalized = normalizeConfidence(value);
        return "high".equals(normalized) && !referenced ? "medium" : normalized;
    }
    private String normalizeConfidence(String value) {
        return switch (StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "low") {
            case "high", "confirmed" -> "high";
            case "medium", "inferred" -> "medium";
            default -> "low";
        };
    }
    private Integer positive(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { var parsed = Integer.parseInt(value); return parsed > 0 ? parsed : null; }
        catch (NumberFormatException exception) { return null; }
    }
    private String firstText(String... values) {
        for (var value : values) if (StringUtils.hasText(value)) return value.trim();
        return "selected element";
    }
}
