package pl.mkn.tdw.features.changeverification.job.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationComplianceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationReportMapperTest {

    @Test
    void shouldPreferAiAuthoredComplianceSectionAndKeepDeterministicComplianceFallback() {
        var result = new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
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
                        "## Wynik weryfikacji\nAI-authored story report.",
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
        assertThat(report.sections()).hasSize(2);
        assertThat(section(report, ChangeVerificationReportSectionIds.STORY_COMPLIANCE).markdown())
                .contains("AI-authored story report");
        assertThat(section(report, ChangeVerificationReportSectionIds.INSTRUCTION_COMPLIANCE).markdown())
                .contains("Brak strukturalnych kryteriów")
                .contains("aktualny kontrakt")
                .doesNotContain("Dodatkowe ustalenia")
                .doesNotContain("fallback")
                .doesNotContain("verificationChecks");
        assertThat(report.meta().visibilityLimits()).contains(
                "Repository dependency was not visible.",
                "Diff visibility is partial."
        );
        assertThat(report.meta().confidence()).isEqualTo("high");
    }

    @Test
    void shouldBuildHumanFirstFallbackWithAttentionBeforeConfirmedChecks() {
        var result = new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "prompt",
                new ChangeVerificationComplianceResponse(
                        true,
                        false,
                        "PASSED_WITH_WARNINGS",
                        List.of(
                                check(
                                        "story-001",
                                        "PASSED",
                                        "Event uruchamia inicjalizację.",
                                        "Przepływ został potwierdzony testem integracyjnym.",
                                        ""
                                ),
                                check(
                                        "story-002",
                                        "WARNING",
                                        "Błąd publikacji nie może zostać pominięty.",
                                        "Wyjątek jest logowany, ale nie jest propagowany.",
                                        "Dodać retry albo zaakceptować ryzyko."
                                )
                        ),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                null
        );

        var markdown = section(
                ChangeVerificationReportMapper.toReport(result),
                ChangeVerificationReportSectionIds.STORY_COMPLIANCE
        ).markdown();

        assertThat(markdown)
                .contains("## Wynik weryfikacji")
                .contains("**Potwierdzone:** 1")
                .contains("**Wymaga uwagi:** 1")
                .contains("## Wymaga uwagi")
                .contains("| Status | Kryterium | Wniosek | Rekomendowane działanie |")
                .contains("## Potwierdzone wymagania")
                .contains("## Szczegóły kryteriów")
                .doesNotContain("criterionSource")
                .doesNotContain("evidenceRefs")
                .doesNotContain("gaps: []");
        assertThat(markdown.indexOf("## Wymaga uwagi"))
                .isLessThan(markdown.indexOf("## Potwierdzone wymagania"));
    }

    private ChangeVerificationVerificationCheckResponse check(
            String id,
            String status,
            String criterion,
            String analysis,
            String suggestedAction
    ) {
        return new ChangeVerificationVerificationCheckResponse(
                id,
                "STORY_COMPLIANCE",
                "Jira acceptance criteria",
                "System powinien opublikować event.",
                "explicit",
                criterion,
                status,
                "backend/src/EventPublisher.java",
                analysis,
                List.of("backend/src/EventPublisher.java"),
                List.of(),
                suggestedAction
        );
    }

    private AnalysisReportSection section(AnalysisReport report, String id) {
        return report.sections().stream()
                .filter(section -> id.equals(section.id()))
                .findFirst()
                .orElseThrow();
    }
}
