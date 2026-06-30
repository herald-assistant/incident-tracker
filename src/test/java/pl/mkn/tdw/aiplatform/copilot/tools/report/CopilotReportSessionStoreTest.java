package pl.mkn.tdw.aiplatform.copilot.tools.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotReportSessionStoreTest {

    @Test
    void shouldRegisterAndReturnCurrentReport() {
        var store = new CopilotReportSessionStore();
        var report = report("report-1", "Initial");

        store.register(report);

        assertEquals(report, store.current("report-1").orElseThrow());
    }

    @Test
    void shouldReplaceRegisteredReportSnapshot() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1", "Initial"));

        var updated = report("report-1", "Updated");
        var snapshot = store.replace(updated);

        assertEquals(updated, snapshot);
        assertEquals("Updated", store.current("report-1").orElseThrow().header());
    }

    @Test
    void shouldUpsertSectionsBySectionId() {
        var store = new CopilotReportSessionStore();
        store.register(new AnalysisReport(
                "report-1",
                "Header",
                null,
                null,
                List.of(section("OVERVIEW", 1, "Old overview")),
                AnalysisReportMeta.empty()
        ));

        store.upsertSection("report-1", section("OVERVIEW", 1, "New overview"));
        var snapshot = store.upsertSection("report-1", section("TECHNICAL", 2, "Technical section"));

        assertEquals(2, snapshot.sections().size());
        assertEquals("New overview", snapshot.sections().get(0).markdown());
        assertEquals("TECHNICAL", snapshot.sections().get(1).id());
        assertEquals(snapshot, store.current("report-1").orElseThrow());
    }

    @Test
    void shouldUpdateReportMeta() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1", "Initial"));
        var meta = new AnalysisReportMeta(
                List.of(new AnalysisReportReference("evidence", "Logs", "elastic/logs", "Initial logs")),
                List.of("Only initial logs are visible."),
                List.of("Is downstream healthy?"),
                List.of("No DB read."),
                "medium",
                List.of("Low source coverage.")
        );

        var snapshot = store.updateMeta("report-1", meta);

        assertEquals(meta, snapshot.meta());
        assertEquals(meta, store.current("report-1").orElseThrow().meta());
    }

    @Test
    void shouldUnregisterReport() {
        var store = new CopilotReportSessionStore();
        var report = report("report-1", "Initial");
        store.register(report);

        var removed = store.unregister("report-1");

        assertEquals(report, removed.orElseThrow());
        assertFalse(store.current("report-1").isPresent());
    }

    @Test
    void shouldRejectMissingOrInvalidReportsForMutations() {
        var store = new CopilotReportSessionStore();

        assertThrows(CopilotReportSessionException.class, () -> store.register(null));
        assertThrows(CopilotReportSessionException.class, () -> store.register(report(" ", "Initial")));
        assertThrows(CopilotReportSessionException.class, () -> store.replace(report("missing", "Updated")));
        assertThrows(CopilotReportSessionException.class, () -> store.upsertSection("missing", section("OVERVIEW", 1, "x")));
        assertThrows(CopilotReportSessionException.class, () -> store.upsertSection("report-1", section(null, 1, "x")));
        assertThrows(CopilotReportSessionException.class, () -> store.updateMeta("report-1", null));
        assertFalse(store.current(" ").isPresent());
        assertFalse(store.unregister(null).isPresent());
    }

    @Test
    void shouldApplyConcurrentSectionUpdatesToLatestSnapshot() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1", "Initial"));
        var executor = Executors.newFixedThreadPool(4);

        try {
            var futures = IntStream.range(0, 20)
                    .mapToObj(index -> CompletableFuture.runAsync(
                            () -> store.upsertSection(
                                    "report-1",
                                    section("SECTION_" + index, index, "Markdown " + index)
                            ),
                            executor
                    ))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(20, store.current("report-1").orElseThrow().sections().size());
    }

    private static AnalysisReport report(String reportId, String header) {
        return new AnalysisReport(
                reportId,
                header,
                "Sub header",
                "Summary",
                List.of(),
                AnalysisReportMeta.empty()
        );
    }

    private static AnalysisReportSection section(String id, Integer order, String markdown) {
        return new AnalysisReportSection(
                id,
                id != null ? id + " title" : null,
                order,
                markdown,
                AnalysisReportMeta.empty()
        );
    }
}
