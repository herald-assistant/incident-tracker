package pl.mkn.tdw.shared.ai.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisReportChangeEvidenceTest {
    @Test
    void capturesOnlyAcceptedTextChangesWithRawBeforeAndAfter() {
        var before = report("Stary opis klienta");
        var after = report("Stary opis klienta\nNowa reguła CRM");

        var section = AnalysisReportChangeEvidence.between(before, after);

        assertThat(section.provider()).isEqualTo("report");
        assertThat(section.category()).isEqualTo("report-change");
        assertThat(section.items()).hasSize(1);
        assertThat(section.items().get(0).title()).isEqualTo("Dane klienta");
        assertThat(section.items().get(0).attributes()).extracting("name", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("before", "Stary opis klienta"),
                        org.assertj.core.groups.Tuple.tuple("after", "Stary opis klienta\nNowa reguła CRM")
                );
        assertThat(AnalysisReportChangeEvidence.between(after, after).hasItems()).isFalse();
    }

    private AnalysisReport report(String markdown) {
        return new AnalysisReport("crm-report", "CRM", "Kontakt", "Podsumowanie CRM",
                List.of(new AnalysisReportSection("data", "Dane klienta", 1, markdown, AnalysisReportMeta.empty())),
                AnalysisReportMeta.empty());
    }
}
