package pl.mkn.tdw.features.uxinspector.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorReportMapperTest {
    private final UxInspectorReportMapper mapper = new UxInspectorReportMapper();

    @Test
    void shouldProjectExactlyOneGroundedAnswer() {
        var reference = new AnalysisReportReference("source", "Przycisk zapisu", TEMPLATE_PATH + "#L1",
                "Powiazanie przycisku z obsluga formularza");
        var sectionMeta = new AnalysisReportMeta(List.of(reference), List.of(), List.of(), List.of(), "high", List.of());
        var report = report("Walidacja formularza blokuje zapis.",
                "Przycisk jest zablokowany, dopoki formularz nie jest poprawny.", sectionMeta, AnalysisReportMeta.empty());

        var mapping = mapper.map(report, capture(), targetContext(), null);

        assertThat(mapping.complete()).isTrue();
        assertThat(mapping.limitations()).isEmpty();
        assertThat(mapping.report().sections()).singleElement().satisfies(section -> {
            assertThat(section.id()).isEqualTo("answer");
            assertThat(section.title()).isEqualTo("Odpowiedz");
        });
        assertThat(mapping.result().sourceReferences()).isEmpty();
        assertThat(mapping.result().confidence()).isEqualTo("high");
    }

    @Test
    void shouldDiscardReferenceMetadataAndKeepOtherReportMetadata() {
        var sectionReference = new AnalysisReportReference(
                "source", "Widok formularza", TEMPLATE_PATH + "#L1", "Opis sekcyjny");
        var globalReference = new AnalysisReportReference(
                "source", "Formularz kontaktu", TEMPLATE_PATH + "#L1", "Inny opis globalny");
        var sectionMeta = new AnalysisReportMeta(
                List.of(sectionReference), List.of("Brak runtime"), List.of("Czy stan jest aktualny?"),
                List.of("Brak API"), "high", List.of("Ostrzezenie sekcyjne"));
        var reportMeta = new AnalysisReportMeta(
                List.of(globalReference), List.of("Brak runtime"), List.of("Czy stan jest aktualny?"),
                List.of("Brak API"), "high", List.of("Ostrzezenie globalne"));

        var mapping = mapper.map(
                report("Walidacja formularza blokuje zapis.", "Odpowiedz", sectionMeta, reportMeta),
                capture(), targetContext(), null);

        assertThat(mapping.result().sourceReferences()).isEmpty();
        assertThat(mapping.report().sections().get(0).meta()).satisfies(meta -> {
            assertThat(meta.references()).isEmpty();
            assertThat(meta.visibilityLimits()).isEmpty();
            assertThat(meta.openQuestions()).isEmpty();
            assertThat(meta.gaps()).isEmpty();
            assertThat(meta.confidence()).isNull();
            assertThat(meta.warnings()).isEmpty();
        });
        assertThat(mapping.report().meta()).satisfies(meta -> {
            assertThat(meta.references()).isEmpty();
            assertThat(meta.visibilityLimits()).containsExactly("Brak runtime");
            assertThat(meta.openQuestions()).containsExactly("Czy stan jest aktualny?");
            assertThat(meta.gaps()).containsExactly("Brak API");
            assertThat(meta.confidence()).isEqualTo("high");
            assertThat(meta.warnings()).containsExactly("Ostrzezenie sekcyjne", "Ostrzezenie globalne");
        });
    }

    @Test
    void shouldRejectExtraSectionsJsonAndMissingHeader() {
        var meta = groundedMeta();
        var extra = new AnalysisReport("report-crm", "Teza", "CRM", "Teza",
                List.of(new AnalysisReportSection("answer", "Odpowiedz", 1, "Odpowiedz", meta),
                        new AnalysisReportSection("overview", "Nie wolno", 2, "Nadmiar", meta)), meta);
        var json = report("Teza", "```json\n{\"answer\":\"x\"}\n```", meta, meta);
        var missingHeader = new AnalysisReport("report-crm", "", "CRM", "",
                List.of(new AnalysisReportSection("answer", "Odpowiedz", 1, "Odpowiedz", meta)), meta);

        assertThat(mapper.map(extra, capture(), targetContext(), null).result()).isNull();
        assertThat(mapper.map(json, capture(), targetContext(), null).result()).isNull();
        assertThat(mapper.map(missingHeader, capture(), targetContext(), null).result()).isNull();
    }

    @Test
    void shouldIgnoreReferencesWithoutRequiringAnEvidenceMarker() {
        var outside = new AnalysisReportMeta(List.of(new AnalysisReportReference("source", "Poza scope",
                "src/app/admin/secrets.ts#L1", "Niezweryfikowany plik")), List.of(), List.of(), List.of(), "high", List.of());
        var empty = AnalysisReportMeta.empty();

        var inferred = mapper.map(report("Teza", "Odpowiedz", outside, empty), capture(), targetContext(), null);
        assertThat(inferred.result()).isNotNull();
        assertThat(inferred.complete()).isTrue();
        assertThat(inferred.result().confidence()).isEqualTo("high");
        assertThat(inferred.result().sourceReferences()).isEmpty();
        assertThat(inferred.report().meta().warnings()).isEmpty();
        assertThat(mapper.map(report("Teza", "Odpowiedz", empty, empty), capture(), targetContext(), null).result())
                .isNotNull();

        var gap = new AnalysisReportMeta(List.of(), List.of(), List.of(),
                List.of("Brak implementacji API w zakresie przypietego frontendu."), "low", List.of());
        var partial = mapper.map(report("Teza", "Nie mozna tego potwierdzic w dostepnym kodzie.", gap, gap),
                capture(), targetContext(), null);
        assertThat(partial.result()).isNotNull();
        assertThat(partial.complete()).isFalse();
        assertThat(partial.result().visibilityLimits()).contains("Brak implementacji API w zakresie przypietego frontendu.");
    }

    @Test
    void shouldKeepTheReportAndDiscardMalformedSourceReference() {
        var malformed = new AnalysisReportMeta(List.of(new AnalysisReportReference(
                "source", "Niepewna sciezka", "../../outside.txt#L9-L2", "Wniosek modelu")),
                List.of(), List.of(), List.of(), "high", List.of());

        var mapping = mapper.map(report("Teza", "Odpowiedz", malformed, AnalysisReportMeta.empty()),
                capture(), targetContext(), null);

        assertThat(mapping.result()).isNotNull();
        assertThat(mapping.complete()).isTrue();
        assertThat(mapping.result().confidence()).isEqualTo("high");
        assertThat(mapping.result().sourceReferences()).isEmpty();
        assertThat(mapping.report().meta().warnings()).isEmpty();
    }

    private AnalysisReport report(String thesis, String answer, AnalysisReportMeta sectionMeta, AnalysisReportMeta reportMeta) {
        return new AnalysisReport("report-crm", "UX Inspector", "CRM", thesis,
                List.of(new AnalysisReportSection("answer", "Odpowiedz", 1, answer, sectionMeta)), reportMeta);
    }

    private AnalysisReportMeta groundedMeta() {
        return new AnalysisReportMeta(List.of(new AnalysisReportReference("source", "Target", TEMPLATE_PATH + "#L1",
                "Zweryfikowany template")), List.of(), List.of(), List.of(), "high", List.of());
    }

}
