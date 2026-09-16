package pl.mkn.tdw.features.uxinspector.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;
import java.util.Set;

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

        var mapping = mapper.map(report, capture(), targetContext(), Set.of(), null);

        assertThat(mapping.complete()).isTrue();
        assertThat(mapping.limitations()).isEmpty();
        assertThat(mapping.report().sections()).singleElement().satisfies(section -> {
            assertThat(section.id()).isEqualTo("answer");
            assertThat(section.title()).isEqualTo("Odpowiedz");
        });
        assertThat(mapping.result().sourceReferences()).singleElement()
                .extracting(AnalysisReportReference::target).isEqualTo(TEMPLATE_PATH + "#L1");
        assertThat(mapping.result().confidence()).isEqualTo("high");
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

        assertThat(mapper.map(extra, capture(), targetContext(), Set.of(), null).result()).isNull();
        assertThat(mapper.map(json, capture(), targetContext(), Set.of(), null).result()).isNull();
        assertThat(mapper.map(missingHeader, capture(), targetContext(), Set.of(), null).result()).isNull();
    }

    @Test
    void shouldRejectOutOfScopeReferencesAndRequireReferenceOrGap() {
        var outside = new AnalysisReportMeta(List.of(new AnalysisReportReference("source", "Poza scope",
                "src/app/admin/secrets.ts#L1", "Niezweryfikowany plik")), List.of(), List.of(), List.of(), "high", List.of());
        var empty = AnalysisReportMeta.empty();

        assertThat(mapper.map(report("Teza", "Odpowiedz", outside, empty), capture(), targetContext(), Set.of(), null).result())
                .isNull();
        assertThat(mapper.map(report("Teza", "Odpowiedz", empty, empty), capture(), targetContext(), Set.of(), null).result())
                .isNull();

        var gap = new AnalysisReportMeta(List.of(), List.of(), List.of(),
                List.of("Brak implementacji API w zakresie przypietego frontendu."), "low", List.of());
        var partial = mapper.map(report("Teza", "Nie mozna tego potwierdzic w dostepnym kodzie.", gap, gap),
                capture(), targetContext(), Set.of(), null);
        assertThat(partial.result()).isNotNull();
        assertThat(partial.complete()).isFalse();
        assertThat(partial.result().visibilityLimits()).contains("Brak implementacji API w zakresie przypietego frontendu.");
    }

    @Test
    void shouldAcceptOnlyRepositoryFilesActuallyReadAtThePinnedCommit() {
        var path = "src/main/java/example/crm/ContactPolicy.java";
        var sourceRef = "gitlab:CRM/crm-ui@" + REVISION + ":" + path;
        var meta = new AnalysisReportMeta(List.of(new AnalysisReportReference(
                "source", "Regula kontaktu", path + "#L20-L28", "Zweryfikowana regula biznesowa")),
                List.of(), List.of(), List.of(), "high", List.of());
        var sourceReport = report("Regula wymaga aktywnego klienta.", "Kontakt mozna zapisac dla aktywnego klienta.",
                meta, AnalysisReportMeta.empty());

        assertThat(mapper.map(sourceReport, capture(), targetContext(), Set.of(), null).result()).isNull();
        assertThat(mapper.map(sourceReport, capture(), targetContext(), Set.of(sourceRef), null).result())
                .isNotNull();
        assertThat(mapper.map(sourceReport, capture(), targetContext(),
                Set.of("gitlab:CRM/crm-ui@0000000000000000000000000000000000000000:" + path), null).result())
                .isNull();
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
