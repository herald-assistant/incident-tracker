package pl.mkn.tdw.features.changeverification.job.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationComplianceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationReportMapperTest {

    @Test
    void shouldPreferAiAuthoredComplianceSectionAndKeepDeterministicSmokeFallback() {
        var result = new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
                "prompt",
                new ChangeVerificationComplianceResponse(
                        true,
                        true,
                        "PASSED_WITH_WARNINGS",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("Diff visibility is partial.")
                ),
                null,
                null,
                null
        );
        var aiReport = new AnalysisReport(
                "runtime-report",
                "AI header",
                null,
                null,
                List.of(new AnalysisReportSection(
                        ChangeVerificationReportSectionIds.STORY_COMPLIANCE,
                        "Story compliance",
                        0,
                        "## Krotkie podsumowanie weryfikacji\nAI-authored story report.",
                        new AnalysisReportMeta(
                                List.of(new AnalysisReportReference(
                                        "jira",
                                        "CRM-123",
                                        "https://jira.example.com/browse/CRM-123",
                                        "Target issue"
                                )),
                                List.of(),
                                List.of("Confirm inferred retry semantics."),
                                List.of(),
                                "high",
                                List.of()
                        )
                )),
                new AnalysisReportMeta(
                        List.of(),
                        List.of("Repository dependency was not visible."),
                        List.of(),
                        List.of(),
                        "high",
                        List.of()
                )
        );

        var report = ChangeVerificationReportMapper.toReport(result, aiReport);

        assertThat(report.header()).isEqualTo("Change Verification: CRM-123");
        assertThat(report.sections()).hasSize(3);
        assertThat(section(report, ChangeVerificationReportSectionIds.STORY_COMPLIANCE).markdown())
                .contains("AI-authored story report");
        assertThat(section(report, ChangeVerificationReportSectionIds.INSTRUCTION_COMPLIANCE).markdown())
                .contains("Brak strukturalnych `verificationChecks`");
        assertThat(section(report, ChangeVerificationReportSectionIds.SMOKE_PACK).markdown())
                .isEqualTo("Smoke pack was not generated.");
        assertThat(report.meta().visibilityLimits()).contains(
                "Repository dependency was not visible.",
                "Diff visibility is partial."
        );
        assertThat(report.meta().confidence()).isEqualTo("high");
    }

    private AnalysisReportSection section(AnalysisReport report, String id) {
        return report.sections().stream()
                .filter(section -> id.equals(section.id()))
                .findFirst()
                .orElseThrow();
    }
}
