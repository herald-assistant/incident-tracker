package pl.mkn.tdw.shared.ai.report;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisReportModelTest {

    @Test
    void shouldNormalizeNullCollectionsAndMetaToEmptyValues() {
        var report = new AnalysisReport("report-1", null, null, null, null, null);
        var section = new AnalysisReportSection("overview", "Overview", 1, null, null);
        var meta = new AnalysisReportMeta(null, null, null, null, null, null);

        assertEquals(List.of(), report.sections());
        assertEquals(AnalysisReportMeta.empty(), report.meta());
        assertFalse(report.hasSections());
        assertEquals(AnalysisReportMeta.empty(), section.meta());
        assertEquals(List.of(), meta.references());
        assertEquals(List.of(), meta.visibilityLimits());
        assertEquals(List.of(), meta.openQuestions());
        assertEquals(List.of(), meta.gaps());
        assertEquals(List.of(), meta.warnings());
    }

    @Test
    void shouldCopyListsDefensively() {
        var references = new ArrayList<AnalysisReportReference>();
        references.add(new AnalysisReportReference("evidence", "Logs", "elastic/logs", "Initial logs"));
        var visibilityLimits = new ArrayList<>(List.of("Only initial logs are visible."));
        var openQuestions = new ArrayList<>(List.of("Is downstream service healthy?"));
        var gaps = new ArrayList<>(List.of("No trace from payment gateway."));
        var warnings = new ArrayList<>(List.of("Low source coverage."));
        var meta = new AnalysisReportMeta(references, visibilityLimits, openQuestions, gaps, "medium", warnings);
        var sections = new ArrayList<AnalysisReportSection>();
        sections.add(new AnalysisReportSection("overview", "Overview", 1, "Summary", meta));

        var report = new AnalysisReport("report-1", "Header", "Sub header", "Summary", sections, meta);

        references.clear();
        visibilityLimits.clear();
        sections.clear();

        assertEquals(1, meta.references().size());
        assertEquals(1, meta.visibilityLimits().size());
        assertEquals(1, report.sections().size());
        assertThrows(UnsupportedOperationException.class, () -> report.sections().add(
                new AnalysisReportSection("extra", "Extra", 2, "", null)
        ));
        assertThrows(UnsupportedOperationException.class, () -> meta.references().clear());
    }
}
