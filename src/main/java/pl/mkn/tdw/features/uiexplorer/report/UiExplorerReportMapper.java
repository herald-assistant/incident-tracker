package pl.mkn.tdw.features.uiexplorer.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UiExplorerReportMapper {

    private static final Pattern SOURCE_TARGET = Pattern.compile("^(?<path>[^#]+?)(?:#L(?<start>\\d+)(?:-L(?<end>\\d+))?)?$");

    public UiExplorerReportMapping map(
            AnalysisReport report,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            Set<String> additionalSourcePaths,
            AnalysisAiUsage usage
    ) {
        if (report == null) {
            return new UiExplorerReportMapping(
                    null,
                    null,
                    false,
                    List.of("UI Explorer session did not save an AnalysisReport through report tools.")
            );
        }

        var allowedPaths = new LinkedHashSet<String>();
        if (context != null) {
            context.sourcePaths().stream().map(this::normalizePath).filter(StringUtils::hasText).forEach(allowedPaths::add);
        }
        if (additionalSourcePaths != null) {
            additionalSourcePaths.stream().map(this::normalizePath).filter(StringUtils::hasText).forEach(allowedPaths::add);
        }

        var mappingLimitations = new LinkedHashSet<String>();
        var sectionsById = sectionsById(report.sections());
        var safeSections = new ArrayList<AnalysisReportSection>();
        var resultSections = new ArrayList<UiExplorerResultSection>();
        var allSectionReferences = new ArrayList<UiExplorerSourceReference>();
        var allSectionsComplete = true;

        for (var assignment : request.resolvedSectionModes()) {
            if (assignment.mode() == pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode.OFF) {
                continue;
            }
            var sectionId = assignment.sectionId().name();
            var sourceSection = sectionsById.get(sectionId);
            var markdown = sourceSection != null && StringUtils.hasText(sourceSection.markdown())
                    ? sourceSection.markdown().trim()
                    : "";
            var safeMeta = safeMeta(
                    sourceSection != null ? sourceSection.meta() : AnalysisReportMeta.empty(),
                    allowedPaths,
                    sectionId,
                    mappingLimitations
            );
            if (!StringUtils.hasText(markdown)) {
                allSectionsComplete = false;
                safeMeta = withGap(safeMeta, "AI did not save this active report section through report tools.");
                mappingLimitations.add("Active report section was not saved: " + sectionId + ".");
            }
            var typedReferences = typedReferences(safeMeta.references());
            allSectionReferences.addAll(typedReferences);
            var coverage = coverage(markdown, safeMeta);
            if (coverage != UiExplorerCoverageStatus.READY) {
                allSectionsComplete = false;
            }
            resultSections.add(new UiExplorerResultSection(
                    assignment.sectionId(),
                    assignment.mode(),
                    coverage,
                    confidence(safeMeta.confidence(), !typedReferences.isEmpty()),
                    markdown,
                    typedReferences,
                    visibilityLimits(safeMeta),
                    safeMeta.openQuestions()
            ));
            safeSections.add(new AnalysisReportSection(
                    sectionId,
                    assignment.sectionId().label(),
                    assignment.sectionId().ordinal(),
                    markdown,
                    safeMeta
            ));
        }

        var safeReportMeta = safeMeta(report.meta(), allowedPaths, "report", mappingLimitations);
        var summary = StringUtils.hasText(report.markdownSummary()) ? report.markdownSummary().trim() : "";
        if (!StringUtils.hasText(summary)) {
            allSectionsComplete = false;
            safeReportMeta = withGap(safeReportMeta, "AI did not save the functional summary through report_update_header.");
            mappingLimitations.add("Functional report summary was not saved through report tools.");
        }
        if (!mappingLimitations.isEmpty()) {
            safeReportMeta = withWarnings(safeReportMeta, mappingLimitations);
        }
        if (hasPartialMeta(safeReportMeta)) {
            allSectionsComplete = false;
        }

        var safeReport = new AnalysisReport(
                report.reportId(),
                screenPath(context),
                componentLabel(context),
                summary,
                safeSections,
                safeReportMeta
        );
        var result = new UiExplorerResultResponse(
                context != null ? context.screen() : null,
                request.scenarioDescription(),
                context != null ? context.sourceRevision() : null,
                summary,
                resultSections,
                confidence(safeReportMeta.confidence(), !allSectionReferences.isEmpty()),
                visibilityLimits(safeReportMeta),
                safeReportMeta.openQuestions(),
                usage
        );
        return new UiExplorerReportMapping(
                result,
                safeReport,
                allSectionsComplete,
                List.copyOf(mappingLimitations)
        );
    }

    private Map<String, AnalysisReportSection> sectionsById(List<AnalysisReportSection> sections) {
        var result = new LinkedHashMap<String, AnalysisReportSection>();
        for (var section : sections != null ? sections : List.<AnalysisReportSection>of()) {
            if (section != null && StringUtils.hasText(section.id())) {
                result.putIfAbsent(section.id().trim().toUpperCase(Locale.ROOT), section);
            }
        }
        return Map.copyOf(result);
    }

    private AnalysisReportMeta safeMeta(
            AnalysisReportMeta meta,
            Set<String> allowedPaths,
            String owner,
            Set<String> mappingLimitations
    ) {
        var source = meta != null ? meta : AnalysisReportMeta.empty();
        var references = new ArrayList<AnalysisReportReference>();
        for (var reference : source.references()) {
            var parsed = parseReference(reference, allowedPaths);
            if (parsed != null) {
                references.add(parsed);
            } else {
                mappingLimitations.add("An unverified source reference was removed from " + owner + ".");
            }
        }
        return new AnalysisReportMeta(
                references,
                source.visibilityLimits(),
                source.openQuestions(),
                source.gaps(),
                normalizeConfidence(source.confidence()),
                source.warnings()
        );
    }

    private AnalysisReportReference parseReference(AnalysisReportReference reference, Set<String> allowedPaths) {
        if (reference == null || !StringUtils.hasText(reference.target())) {
            return null;
        }
        var matcher = SOURCE_TARGET.matcher(reference.target().trim());
        if (!matcher.matches()) {
            return null;
        }
        var path = normalizePath(matcher.group("path"));
        if (!StringUtils.hasText(path) || !allowedPaths.contains(path)) {
            return null;
        }
        var startLine = positiveInteger(matcher.group("start"));
        var endLine = positiveInteger(matcher.group("end"));
        if (matcher.group("start") != null && startLine == null
                || matcher.group("end") != null && (endLine == null || startLine == null || endLine < startLine)) {
            return null;
        }
        var normalizedTarget = path;
        if (startLine != null) {
            normalizedTarget += "#L" + startLine;
            if (endLine != null && !endLine.equals(startLine)) {
                normalizedTarget += "-L" + endLine;
            }
        }
        return new AnalysisReportReference(
                "source",
                StringUtils.hasText(reference.label()) ? reference.label().trim() : path,
                normalizedTarget,
                StringUtils.hasText(reference.description()) ? reference.description().trim() : "UI source"
        );
    }

    private List<UiExplorerSourceReference> typedReferences(List<AnalysisReportReference> references) {
        var result = new ArrayList<UiExplorerSourceReference>();
        for (var reference : references) {
            var matcher = SOURCE_TARGET.matcher(reference.target());
            if (!matcher.matches()) {
                continue;
            }
            result.add(new UiExplorerSourceReference(
                    null,
                    matcher.group("path"),
                    normalize(reference.label()),
                    positiveInteger(matcher.group("start")),
                    positiveInteger(matcher.group("end"))
            ));
        }
        return List.copyOf(result);
    }

    private UiExplorerCoverageStatus coverage(String markdown, AnalysisReportMeta meta) {
        if (!StringUtils.hasText(markdown)) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        return hasPartialMeta(meta) ? UiExplorerCoverageStatus.PARTIAL : UiExplorerCoverageStatus.READY;
    }

    private boolean hasPartialMeta(AnalysisReportMeta meta) {
        return meta != null && (!meta.visibilityLimits().isEmpty()
                || !meta.openQuestions().isEmpty()
                || !meta.gaps().isEmpty()
                || !meta.warnings().isEmpty());
    }

    private UiExplorerClaimConfidence confidence(String value, boolean hasReferences) {
        var normalized = normalizeConfidence(value);
        if ("high".equals(normalized) && hasReferences) {
            return UiExplorerClaimConfidence.CONFIRMED;
        }
        if ("high".equals(normalized) || "medium".equals(normalized)) {
            return UiExplorerClaimConfidence.INFERRED;
        }
        return UiExplorerClaimConfidence.UNKNOWN;
    }

    private String normalizeConfidence(String value) {
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "high", "confirmed" -> "high";
            case "medium", "inferred" -> "medium";
            default -> "low";
        };
    }

    private List<String> visibilityLimits(AnalysisReportMeta meta) {
        var values = new LinkedHashSet<String>();
        values.addAll(meta.visibilityLimits());
        meta.gaps().stream().filter(StringUtils::hasText).map(value -> "Gap: " + value.trim()).forEach(values::add);
        meta.warnings().stream().filter(StringUtils::hasText).map(value -> "Warning: " + value.trim()).forEach(values::add);
        return List.copyOf(values);
    }

    private AnalysisReportMeta withGap(AnalysisReportMeta meta, String gap) {
        var gaps = new LinkedHashSet<>(meta.gaps());
        gaps.add(gap);
        return new AnalysisReportMeta(
                meta.references(), meta.visibilityLimits(), meta.openQuestions(), List.copyOf(gaps),
                meta.confidence(), meta.warnings()
        );
    }

    private AnalysisReportMeta withWarnings(AnalysisReportMeta meta, Set<String> warningsToAdd) {
        var warnings = new LinkedHashSet<>(meta.warnings());
        warnings.addAll(warningsToAdd);
        return new AnalysisReportMeta(
                meta.references(), meta.visibilityLimits(), meta.openQuestions(), meta.gaps(),
                meta.confidence(), List.copyOf(warnings)
        );
    }

    private String screenPath(UiExplorerScreenReachabilityContext context) {
        if (context != null && context.screen() != null && StringUtils.hasText(context.screen().routePattern())) {
            return context.screen().routePattern().trim();
        }
        return context != null && context.screen() != null ? context.screen().screenId() : "screen";
    }

    private String componentLabel(UiExplorerScreenReachabilityContext context) {
        if (context != null && context.screen() != null && StringUtils.hasText(context.screen().label())) {
            return context.screen().label().trim();
        }
        return "Component unavailable";
    }

    private Integer positiveInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            var parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizePath(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "")
                : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
