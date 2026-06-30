package pl.mkn.tdw.features.incidentanalysis.ai.copilot.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentReportSectionIds;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
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
public class CopilotIncidentReportMapper {

    private static final String REFERENCE_PROCESS = "process";
    private static final String REFERENCE_BOUNDED_CONTEXT = "boundedContext";
    private static final String REFERENCE_TEAM = "team";

    public Optional<InitialAnalysisResponse> tryMap(
            AnalysisReport report,
            String prompt,
            AnalysisAiUsage usage,
            String copilotSessionId
    ) {
        if (report == null || !StringUtils.hasText(report.header())) {
            return Optional.empty();
        }

        var sectionsById = sectionsById(report.sections());
        var functionalAnalysis = markdown(sectionsById.get(CopilotIncidentReportSectionIds.FUNCTIONAL_ANALYSIS));
        var technicalAnalysis = markdown(sectionsById.get(CopilotIncidentReportSectionIds.TECHNICAL_HANDOFF));
        if (!StringUtils.hasText(functionalAnalysis) || !StringUtils.hasText(technicalAnalysis)) {
            return Optional.empty();
        }

        return Optional.of(new InitialAnalysisResponse(
                "copilot-sdk",
                report.header().trim(),
                referenceValue(report.meta(), REFERENCE_PROCESS),
                referenceValue(report.meta(), REFERENCE_BOUNDED_CONTEXT),
                referenceValue(report.meta(), REFERENCE_TEAM),
                functionalAnalysis,
                technicalAnalysis,
                confidence(report.meta()),
                visibilityLimits(report, sectionsById),
                prompt,
                usage,
                copilotSessionId,
                report
        ));
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

    private String markdown(AnalysisReportSection section) {
        return section != null && StringUtils.hasText(section.markdown())
                ? section.markdown().trim()
                : null;
    }

    private String referenceValue(AnalysisReportMeta meta, String type) {
        if (meta == null) {
            return "nieustalone";
        }
        return meta.references().stream()
                .filter(reference -> reference != null && type.equalsIgnoreCase(nullToEmpty(reference.type())))
                .map(this::referenceText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("nieustalone");
    }

    private String referenceText(AnalysisReportReference reference) {
        if (StringUtils.hasText(reference.label())) {
            return reference.label().trim();
        }
        if (StringUtils.hasText(reference.target())) {
            return reference.target().trim();
        }
        if (StringUtils.hasText(reference.description())) {
            return reference.description().trim();
        }
        return null;
    }

    private List<String> visibilityLimits(
            AnalysisReport report,
            Map<String, AnalysisReportSection> sectionsById
    ) {
        var limits = new ArrayList<String>();
        addMetaLimits(limits, report.meta());
        for (var sectionId : CopilotIncidentReportSectionIds.INITIAL_ALLOWED_SECTION_IDS) {
            var section = sectionsById.get(sectionId);
            if (section != null) {
                addMetaLimits(limits, section.meta());
            }
        }
        return List.copyOf(limits);
    }

    private void addMetaLimits(List<String> limits, AnalysisReportMeta meta) {
        if (meta == null) {
            return;
        }
        addAll(limits, meta.visibilityLimits(), "");
        addAll(limits, meta.gaps(), "Gap: ");
        addAll(limits, meta.warnings(), "Warning: ");
    }

    private void addAll(List<String> target, List<String> values, String prefix) {
        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                target.add(prefix + value.trim());
            }
        }
    }

    private String confidence(AnalysisReportMeta meta) {
        var value = meta != null ? meta.confidence() : null;
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
