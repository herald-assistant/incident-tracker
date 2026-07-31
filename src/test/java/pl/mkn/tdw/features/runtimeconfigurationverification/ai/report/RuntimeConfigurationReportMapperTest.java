package pl.mkn.tdw.features.runtimeconfigurationverification.ai.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationReportMapperTest {

    private final RuntimeConfigurationReportFactory factory = new RuntimeConfigurationReportFactory();
    private final RuntimeConfigurationReportMapper mapper = new RuntimeConfigurationReportMapper();

    @Test
    void shouldAllowOnlyAiSectionsAndRestoreHeaderReferencesOwnershipAndDeterministicContent() {
        var deterministic = RuntimeConfigurationAiTestFixtures.deterministic(
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var deep = RuntimeConfigurationAiTestFixtures.deep();
        var scaffold = factory.createInitialReport("report-1", deterministic, deep);
        var malicious = new AnalysisReport(
                "other-report",
                "AI changed header",
                "AI changed scope",
                "AI changed summary",
                List.of(
                        section(RuntimeConfigurationReportSectionIds.DETERMINISTIC_DIFFERENCES, "AI removed diff"),
                        section(RuntimeConfigurationReportSectionIds.DETERMINISTIC_FINDINGS, "AI removed finding"),
                        section(RuntimeConfigurationReportSectionIds.AI_SECOND_OPINION, "Grounded second opinion"),
                        section(RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING, "AI narrative"),
                        section(RuntimeConfigurationReportSectionIds.OWNERSHIP_AND_HANDOFF, "AI selected another owner")
                ),
                new AnalysisReportMeta(List.of(), List.of(), List.of(), List.of(), "high", List.of())
        );

        var merged = mapper.merge(
                scaffold,
                malicious,
                opinion()
        );

        assertThat(merged.reportId()).isEqualTo("report-1");
        assertThat(merged.header()).isEqualTo("Runtime Configuration Verification");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.DETERMINISTIC_DIFFERENCES).markdown())
                .contains("difference-1")
                .doesNotContain("AI removed diff");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.DETERMINISTIC_FINDINGS).markdown())
                .contains("finding-1")
                .doesNotContain("AI removed finding");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.OWNERSHIP_AND_HANDOFF).markdown())
                .contains("Resolution: `unknown`")
                .doesNotContain("another owner");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.AI_SECOND_OPINION).markdown())
                .isEqualTo("Grounded second opinion");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING).markdown())
                .isEqualTo("AI narrative");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING)
                .meta().references()).isEqualTo(
                section(scaffold, RuntimeConfigurationReportSectionIds.FUNCTIONAL_IMPACT_AND_CODE_GROUNDING)
                        .meta().references()
        );
    }

    @Test
    void shouldUseTypedFallbackWhenAiReportIsMissing() {
        var scaffold = factory.createInitialReport(
                "report-1",
                RuntimeConfigurationAiTestFixtures.deterministic(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED),
                null
        );

        var merged = mapper.merge(scaffold, null, opinion());

        assertThat(section(merged, RuntimeConfigurationReportSectionIds.AI_SECOND_OPINION).markdown())
                .contains("REVIEW_REQUIRED")
                .contains("Summary");
        assertThat(section(merged, RuntimeConfigurationReportSectionIds.RECOMMENDED_HUMAN_CHECKS).markdown())
                .contains("Sprawdź ręcznie");
    }

    private AnalysisReportSection section(String id, String markdown) {
        return new AnalysisReportSection(id, "AI title", 99, markdown, AnalysisReportMeta.empty());
    }

    private AnalysisReportSection section(AnalysisReport report, String id) {
        return report.sections().stream().filter(section -> id.equals(section.id())).findFirst().orElseThrow();
    }

    private RuntimeConfigurationAiSecondOpinion opinion() {
        return new RuntimeConfigurationAiSecondOpinion(
                RuntimeConfigurationAiExecutionStatus.COMPLETED,
                RuntimeConfigurationAiConclusion.REVIEW_REQUIRED,
                RuntimeConfigurationAiConfidence.MEDIUM,
                "Summary",
                List.of(),
                List.of("Sprawdź ręcznie"),
                List.of(),
                List.of()
        );
    }
}
