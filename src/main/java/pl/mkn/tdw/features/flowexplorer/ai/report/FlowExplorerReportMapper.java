package pl.mkn.tdw.features.flowexplorer.ai.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class FlowExplorerReportMapper {

    public Optional<FlowExplorerAiResponse> tryMap(
            AnalysisReport report,
            FlowExplorerAnalysisGoal requestedGoal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        if (report == null) {
            return Optional.empty();
        }

        var sectionsById = sectionsById(report.sections());
        var overviewSection = sectionsById.get(FlowExplorerReportSectionIds.OVERVIEW);
        var overviewMarkdown = markdownOrFallback(overviewSection, report.markdownSummary());
        if (!StringUtils.hasText(overviewMarkdown)) {
            return Optional.empty();
        }

        var assignments = FlowExplorerResultSectionModeResolver.activeOnly(sectionModes);
        var resultSections = new ArrayList<FlowExplorerResultSection>();
        for (var assignment : assignments) {
            if (assignment == null || assignment.id() == null) {
                return Optional.empty();
            }
            var reportSection = sectionsById.get(assignment.id().name());
            if (reportSection == null || !StringUtils.hasText(reportSection.markdown())) {
                return Optional.empty();
            }
            resultSections.add(toResultSection(reportSection, assignment));
        }

        return Optional.of(new FlowExplorerAiResponse(
                requestedGoal,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview(
                        overviewMarkdown,
                        confidence(overviewSection != null ? overviewSection.meta() : null, report.meta()),
                        references(overviewSection != null ? overviewSection.meta() : report.meta())
                ),
                resultSections,
                visibilityLimits(report.meta()),
                report.meta().openQuestions(),
                references(report.meta()),
                confidence(report.meta(), null),
                List.of()
        ));
    }

    private FlowExplorerResultSection toResultSection(
            AnalysisReportSection reportSection,
            FlowExplorerResultSectionModeAssignment assignment
    ) {
        return new FlowExplorerResultSection(
                FlowExplorerResultSectionId.valueOf(assignment.id().name()),
                assignment.title(),
                assignment.mode(),
                reportSection.markdown(),
                references(reportSection.meta()),
                visibilityLimits(reportSection.meta()),
                reportSection.meta().openQuestions()
        );
    }

    private Map<String, AnalysisReportSection> sectionsById(List<AnalysisReportSection> sections) {
        var sectionsById = new LinkedHashMap<String, AnalysisReportSection>();
        for (var section : sections != null ? sections : List.<AnalysisReportSection>of()) {
            if (section == null || !StringUtils.hasText(section.id())) {
                continue;
            }
            sectionsById.putIfAbsent(section.id().trim().toUpperCase(Locale.ROOT), section);
        }
        return Map.copyOf(sectionsById);
    }

    private String markdownOrFallback(AnalysisReportSection section, String fallback) {
        if (section != null && StringUtils.hasText(section.markdown())) {
            return section.markdown().trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private List<String> references(AnalysisReportMeta meta) {
        if (meta == null) {
            return List.of();
        }
        return meta.references().stream()
                .map(this::referenceText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String referenceText(AnalysisReportReference reference) {
        if (reference == null) {
            return null;
        }
        if (StringUtils.hasText(reference.target())) {
            return reference.target().trim();
        }
        if (StringUtils.hasText(reference.label())) {
            return reference.label().trim();
        }
        if (StringUtils.hasText(reference.description())) {
            return reference.description().trim();
        }
        return StringUtils.hasText(reference.type()) ? reference.type().trim() : null;
    }

    private List<String> visibilityLimits(AnalysisReportMeta meta) {
        if (meta == null) {
            return List.of();
        }
        var values = new ArrayList<String>();
        values.addAll(meta.visibilityLimits());
        addPrefixed(values, "Gap: ", meta.gaps());
        addPrefixed(values, "Warning: ", meta.warnings());
        return List.copyOf(values);
    }

    private void addPrefixed(List<String> target, String prefix, List<String> values) {
        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                target.add(prefix + value.trim());
            }
        }
    }

    private String confidence(AnalysisReportMeta primary, AnalysisReportMeta fallback) {
        var value = primary != null && StringUtils.hasText(primary.confidence())
                ? primary.confidence()
                : fallback != null ? fallback.confidence() : null;
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }
}
