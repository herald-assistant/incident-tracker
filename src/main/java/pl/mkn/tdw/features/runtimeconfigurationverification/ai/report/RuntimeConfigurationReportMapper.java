package pl.mkn.tdw.features.runtimeconfigurationverification.ai.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.LinkedHashMap;

@Component
public class RuntimeConfigurationReportMapper {

    public AnalysisReport merge(
            AnalysisReport scaffold,
            AnalysisReport aiReport,
            RuntimeConfigurationAiSecondOpinion opinion
    ) {
        if (scaffold == null) {
            return aiReport;
        }
        var aiSections = new LinkedHashMap<String, AnalysisReportSection>();
        if (aiReport != null) {
            aiReport.sections().forEach(section -> aiSections.put(section.id(), section));
        }
        var allowed = RuntimeConfigurationReportSectionIds.aiWritable();
        var sections = scaffold.sections().stream()
                .map(section -> {
                    if (!allowed.contains(section.id())) {
                        return section;
                    }
                    var candidate = aiSections.get(section.id());
                    var markdown = candidate != null && StringUtils.hasText(candidate.markdown())
                            ? candidate.markdown()
                            : fallbackMarkdown(section.id(), opinion);
                    return new AnalysisReportSection(
                            section.id(),
                            section.title(),
                            section.order(),
                            markdown,
                            section.meta()
                    );
                })
                .toList();
        return new AnalysisReport(
                scaffold.reportId(),
                scaffold.header(),
                scaffold.subHeader(),
                scaffold.markdownSummary(),
                sections,
                scaffold.meta()
        );
    }

    private String fallbackMarkdown(String sectionId, RuntimeConfigurationAiSecondOpinion opinion) {
        if (opinion == null) {
            return "AI second opinion is unavailable.";
        }
        if (RuntimeConfigurationReportSectionIds.AI_SECOND_OPINION.equals(sectionId)) {
            var lines = new java.util.ArrayList<String>();
            lines.add("- Conclusion: `" + opinion.conclusion() + "`");
            lines.add("- Confidence: `" + opinion.confidence() + "`");
            lines.add("");
            lines.add(opinion.summary());
            opinion.observations().forEach(observation -> lines.add("- **"
                    + observation.type() + "** `" + observation.observationId() + "`: " + observation.summary()));
            return String.join("\n", lines);
        }
        if (RuntimeConfigurationReportSectionIds.RECOMMENDED_HUMAN_CHECKS.equals(sectionId)) {
            return opinion.recommendedHumanChecks().isEmpty()
                    ? "No additional human checks were proposed."
                    : opinion.recommendedHumanChecks().stream()
                    .map(value -> "- " + value)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        if (RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING.equals(sectionId)) {
            return opinion.functionalImpacts().isEmpty()
                    ? "No functional impact was grounded."
                    : opinion.functionalImpacts().stream()
                    .map(value -> "- **" + value.affectedFunctionality() + "**: " + value.impact())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        return "AI content is unavailable.";
    }
}
