package pl.mkn.tdw.shared.ai.report;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalysisReportManualEditorTest {
    @Test
    void shouldExposeRevisionDigestAndRoundTripManualMarker() throws Exception {
        var mapper = Jackson2ObjectMapperBuilder.json().build();
        var edited = AnalysisReportManualEditor.apply(report(), new AnalysisReportEditRequest(
                report().revisionSha256(), null,
                List.of(new AnalysisReportSectionEdit("OVERVIEW", "Zmiana CRM."))));
        var json = mapper.writeValueAsString(edited);
        assertNotNull(mapper.readTree(json).get("revisionSha256"));
        var restored = mapper.readValue(json, AnalysisReport.class);
        assertEquals(edited.manualEdit(), restored.manualEdit());
        assertEquals(edited.revisionSha256(), restored.revisionSha256());
    }

    @Test
    void shouldChangeOnlyRequestedMarkdownAndMarkRevision() {
        var report = report();
        var changed = AnalysisReportManualEditor.apply(report,
                new AnalysisReportEditRequest(report.revisionSha256(), null,
                        List.of(new AnalysisReportSectionEdit("OVERVIEW", "Poprawiona sprawa CRM."))));
        assertEquals("Sprawa CRM.", report.sections().get(0).markdown());
        assertEquals("Poprawiona sprawa CRM.", changed.sections().get(0).markdown());
        assertEquals(report.sections().get(0).meta(), changed.sections().get(0).meta());
        assertEquals(List.of("OVERVIEW"), changed.manualEdit().changedParts());
        assertEquals(1, changed.manualEdit().revision());
        assertNotEquals(report.revisionSha256(), changed.revisionSha256());
        var again = AnalysisReportManualEditor.apply(changed,
                new AnalysisReportEditRequest(changed.revisionSha256(), "Nowe podsumowanie CRM.", List.of()));
        assertEquals(2, again.manualEdit().revision());
        assertEquals(List.of("OVERVIEW", "markdownSummary"), again.manualEdit().changedParts());
        var guidance = AnalysisReportManualEditGuidance.forFollowUp(again);
        org.junit.jupiter.api.Assertions.assertTrue(guidance.contains("rewizja 2"));
        org.junit.jupiter.api.Assertions.assertTrue(guidance.contains("OVERVIEW, markdownSummary"));
        org.junit.jupiter.api.Assertions.assertTrue(guidance.contains("report_read_section_chunk"));
    }

    @Test
    void shouldRejectStaleUnknownAndBlankWithoutChangingSource() {
        var report = report();
        assertThrows(AnalysisReportEditException.class, () -> AnalysisReportManualEditor.apply(report,
                new AnalysisReportEditRequest("stale", null,
                        List.of(new AnalysisReportSectionEdit("OVERVIEW", "Nowy CRM.")))));
        assertThrows(AnalysisReportEditException.class, () -> AnalysisReportManualEditor.apply(report,
                new AnalysisReportEditRequest(report.revisionSha256(), null,
                        List.of(new AnalysisReportSectionEdit("UNKNOWN", "Nowy CRM.")))));
        assertThrows(AnalysisReportEditException.class, () -> AnalysisReportManualEditor.apply(report,
                new AnalysisReportEditRequest(report.revisionSha256(), null,
                        List.of(new AnalysisReportSectionEdit("OVERVIEW", " ")))));
        assertEquals("Sprawa CRM.", report.sections().get(0).markdown());
    }

    private AnalysisReport report() {
        return new AnalysisReport("crm-report", "CRM", "Sprawa klienta", "Podsumowanie CRM.",
                List.of(new AnalysisReportSection("OVERVIEW", "Opis", 0, "Sprawa CRM.",
                        AnalysisReportMeta.empty())), AnalysisReportMeta.empty());
    }
}
