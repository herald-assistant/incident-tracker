package pl.mkn.tdw.features.configdriftviewer.ai.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiTestFixtures;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDriftViewerReportMapperTest {

    private final ConfigDriftViewerReportFactory factory = new ConfigDriftViewerReportFactory();
    private final ConfigDriftViewerReportMapper mapper = new ConfigDriftViewerReportMapper();

    @Test
    void shouldAllowOnlyAiSectionsAndRestoreHeaderReferencesOwnershipAndDeterministicContent() {
        var deterministic = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var deep = ConfigDriftViewerAiTestFixtures.deep();
        var scaffold = factory.createInitialReport("report-1", deterministic, deep);
        var malicious = new AnalysisReport(
                "other-report",
                "AI changed header",
                "AI changed scope",
                "AI changed summary",
                List.of(
                        section(ConfigDriftViewerReportSectionIds.DETERMINISTIC_DIFFERENCES, "AI removed diff"),
                        section(ConfigDriftViewerReportSectionIds.DETERMINISTIC_FINDINGS, "AI removed finding"),
                        section(ConfigDriftViewerReportSectionIds.AI_SECOND_OPINION, "Grounded second opinion"),
                        section(ConfigDriftViewerReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING, "AI narrative"),
                        section(ConfigDriftViewerReportSectionIds.OWNERSHIP_AND_HANDOFF, "AI selected another owner")
                ),
                new AnalysisReportMeta(List.of(), List.of(), List.of(), List.of(), "high", List.of())
        );

        var merged = mapper.merge(
                scaffold,
                malicious,
                opinion()
        );

        assertThat(merged.reportId()).isEqualTo("report-1");
        assertThat(merged.header()).isEqualTo("Config Drift Viewer");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.DETERMINISTIC_DIFFERENCES).markdown())
                .contains("difference-1")
                .doesNotContain("AI removed diff");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.DETERMINISTIC_FINDINGS).markdown())
                .contains("finding-1")
                .doesNotContain("AI removed finding");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.OWNERSHIP_AND_HANDOFF).markdown())
                .contains("Resolution: `unknown`")
                .doesNotContain("another owner");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.AI_SECOND_OPINION).markdown())
                .isEqualTo("Grounded second opinion");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING).markdown())
                .isEqualTo("AI narrative");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING)
                .meta().references()).isEqualTo(
                section(scaffold, ConfigDriftViewerReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING)
                        .meta().references()
        );
    }

    @Test
    void shouldUseTypedFallbackWhenAiReportIsMissing() {
        var scaffold = factory.createInitialReport(
                "report-1",
                ConfigDriftViewerAiTestFixtures.deterministic(ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED),
                null
        );

        var merged = mapper.merge(scaffold, null, opinion());

        assertThat(section(merged, ConfigDriftViewerReportSectionIds.AI_SECOND_OPINION).markdown())
                .contains("REVIEW_REQUIRED")
                .contains("Summary");
        assertThat(section(merged, ConfigDriftViewerReportSectionIds.RECOMMENDED_HUMAN_CHECKS).markdown())
                .contains("Sprawdź ręcznie");
    }

    private AnalysisReportSection section(String id, String markdown) {
        return new AnalysisReportSection(id, "AI title", 99, markdown, AnalysisReportMeta.empty());
    }

    private AnalysisReportSection section(AnalysisReport report, String id) {
        return report.sections().stream().filter(section -> id.equals(section.id())).findFirst().orElseThrow();
    }

    private ConfigDriftViewerAiSecondOpinion opinion() {
        return new ConfigDriftViewerAiSecondOpinion(
                ConfigDriftViewerAiExecutionStatus.COMPLETED,
                ConfigDriftViewerAiConclusion.REVIEW_REQUIRED,
                ConfigDriftViewerAiConfidence.MEDIUM,
                "Summary",
                List.of(),
                List.of("Sprawdź ręcznie"),
                List.of(),
                List.of()
        );
    }
}
