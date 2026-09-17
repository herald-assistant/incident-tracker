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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UxInspectorReportMapper {
    private static final Pattern SOURCE_TARGET = Pattern.compile("^(?<path>[^#]+?)(?:#L(?<start>\\d+)(?:-L(?<end>\\d+))?)?$");
    private static final String VERIFIED_SOURCE_TYPE = "source";
    private static final String UNVERIFIED_SOURCE_TYPE = "source-unverified";
    private static final String UNVERIFIED_DESCRIPTION =
            "Niezweryfikowana referencja: plik nie zostal udostepniony modelowi jako potwierdzone evidence.";

    public UxInspectorReportMapping map(AnalysisReport report, UxInspectorCapture capture,
                                        UxInspectorTargetContext context, Set<String> initialSourcePaths,
                                        Set<String> toolReadSourceRefs,
                                        AnalysisAiUsage usage) {
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
        var allowedSourcePaths = allowedSourcePaths(context, initialSourcePaths, toolReadSourceRefs);
        var validation = new ReferenceValidationState();
        var sectionMeta = validateMeta(sourceSection.meta(), allowedSourcePaths, validation);
        var reportMeta = validateMeta(report.meta(), allowedSourcePaths, validation);
        var references = references(sectionMeta.references(), reportMeta.references());
        var gaps = new LinkedHashSet<String>();
        gaps.addAll(sectionMeta.gaps());
        gaps.addAll(reportMeta.gaps());
        var limits = new LinkedHashSet<String>();
        limits.addAll(sectionMeta.visibilityLimits());
        limits.addAll(reportMeta.visibilityLimits());
        limits.addAll(gaps);
        if (references.isEmpty() && limits.isEmpty() && validation.unverifiedCount == 0) {
            return failed("UX Inspector answer has no verified source reference and no explicit evidence gap.");
        }
        limits.addAll(context.limitations());
        var openQuestions = new LinkedHashSet<String>();
        openQuestions.addAll(sectionMeta.openQuestions());
        openQuestions.addAll(reportMeta.openQuestions());
        var confidence = confidence(reportMeta.confidence(), sectionMeta.confidence(), validation.verifiedCount > 0);
        var warnings = new LinkedHashSet<String>();
        warnings.addAll(sectionMeta.warnings());
        warnings.addAll(reportMeta.warnings());
        var canonicalSectionMeta = new AnalysisReportMeta(
                references, List.of(), List.of(), List.of(), null, List.of());
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
                confidence, List.copyOf(references), List.copyOf(limits), List.copyOf(openQuestions), usage);
        var complete = limits.isEmpty() && validation.unverifiedCount == 0
                && context.status() == pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus.RESOLVED;
        return new UxInspectorReportMapping(result, safeReport, complete, List.copyOf(limits));
    }

    private List<AnalysisReportReference> references(
            List<AnalysisReportReference> sectionReferences,
            List<AnalysisReportReference> reportReferences
    ) {
        Map<String, AnalysisReportReference> byTarget = new LinkedHashMap<>();
        for (var reference : sectionReferences) {
            if (reference != null) byTarget.putIfAbsent(reference.target(), reference);
        }
        for (var reference : reportReferences) {
            if (reference != null) byTarget.putIfAbsent(reference.target(), reference);
        }
        return List.copyOf(byTarget.values());
    }

    private List<String> distinct(List<String> sectionValues, List<String> reportValues) {
        var values = new LinkedHashSet<String>();
        values.addAll(sectionValues);
        values.addAll(reportValues);
        return List.copyOf(values);
    }

    private AnalysisReportMeta validateMeta(AnalysisReportMeta source, Set<String> allowedSourcePaths,
                                            ReferenceValidationState validation) {
        var meta = source != null ? source : AnalysisReportMeta.empty();
        var references = new ArrayList<AnalysisReportReference>();
        var warnings = new LinkedHashSet<>(meta.warnings());
        for (var reference : meta.references()) {
            var outcome = reference(reference, allowedSourcePaths);
            if (outcome.reference() != null) references.add(outcome.reference());
            if (outcome.warning() != null) warnings.add(outcome.warning());
            if (outcome.verified()) validation.verifiedCount++;
            else validation.unverifiedCount++;
        }
        return new AnalysisReportMeta(references, meta.visibilityLimits(), meta.openQuestions(), meta.gaps(),
                StringUtils.hasText(meta.confidence()) ? normalizeConfidence(meta.confidence()) : null,
                List.copyOf(warnings));
    }

    private ReferenceOutcome reference(AnalysisReportReference value, Set<String> allowedSourcePaths) {
        if (value == null || !StringUtils.hasText(value.target())) {
            return ReferenceOutcome.unverified(null,
                    "Raport zawieral referencje bez targetu; nie mozna bylo jej zweryfikowac.");
        }
        var matcher = SOURCE_TARGET.matcher(value.target().trim().replace('\\', '/'));
        if (!matcher.matches()) return unverified(value,
                "Referencja nie ma obslugiwanego formatu path[#Lstart-Lend]: " + value.target().trim());
        var path = matcher.group("path").replaceAll("^/+", "");
        var start = positive(matcher.group("start"));
        var end = positive(matcher.group("end"));
        if (!GitLabVerifiedRepositoryFileReader.isSafePath(path, false)
                || matcher.group("start") != null && start == null
                || matcher.group("end") != null && (start == null || end == null || end < start)) {
            return unverified(value, "Referencja nie ma bezpiecznej sciezki albo poprawnego zakresu linii: "
                    + value.target().trim());
        }
        var target = path + (start != null ? "#L" + start + (end != null && !end.equals(start) ? "-L" + end : "") : "");
        if (!allowedSourcePaths.contains(path)) {
            return unverified(value, target,
                    "Nie zweryfikowano odczytu pliku wskazanego przez model: " + path);
        }
        return ReferenceOutcome.verified(new AnalysisReportReference(
                VERIFIED_SOURCE_TYPE, label(value, path), target,
                StringUtils.hasText(value.description()) ? value.description().trim() : "Pinned frontend source"));
    }

    private ReferenceOutcome unverified(AnalysisReportReference value, String warning) {
        return unverified(value, value.target().trim().replace('\\', '/'), warning);
    }

    private ReferenceOutcome unverified(AnalysisReportReference value, String target, String warning) {
        var description = StringUtils.hasText(value.description())
                ? value.description().trim() + " " + UNVERIFIED_DESCRIPTION
                : UNVERIFIED_DESCRIPTION;
        return ReferenceOutcome.unverified(new AnalysisReportReference(
                UNVERIFIED_SOURCE_TYPE, label(value, target), target, description), warning);
    }

    private String label(AnalysisReportReference value, String fallback) {
        return StringUtils.hasText(value.label()) ? value.label().trim() : fallback;
    }

    private Set<String> allowedSourcePaths(
            UxInspectorTargetContext context,
            Set<String> initialSourcePaths,
            Set<String> toolReadSourceRefs
    ) {
        var paths = new LinkedHashSet<String>();
        for (var path : initialSourcePaths != null ? initialSourcePaths : Set.<String>of()) {
            if (GitLabVerifiedRepositoryFileReader.isSafePath(path, false)) paths.add(path);
        }
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

    private record ReferenceOutcome(AnalysisReportReference reference, boolean verified, String warning) {
        private static ReferenceOutcome verified(AnalysisReportReference reference) {
            return new ReferenceOutcome(reference, true, null);
        }

        private static ReferenceOutcome unverified(AnalysisReportReference reference, String warning) {
            return new ReferenceOutcome(reference, false, warning);
        }
    }

    private static final class ReferenceValidationState {
        private int verifiedCount;
        private int unverifiedCount;
    }
}
