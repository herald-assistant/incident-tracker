package pl.mkn.tdw.aiplatform.copilot.tools.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotReportToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRegisterReportToolCallbacksWithoutModelFacingReportId() throws JsonProcessingException {
        var store = new CopilotReportSessionStore();
        var tools = new CopilotReportTools(store);
        var provider = new CopilotReportToolConfiguration().copilotReportToolCallbackProvider(tools);
        var callbacksByName = Arrays.stream(provider.getToolCallbacks())
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(),
                        callback -> callback
                ));

        assertTrue(callbacksByName.keySet().containsAll(CopilotReportToolNames.allToolNames()));
        for (var toolName : CopilotReportToolNames.allToolNames()) {
            var schema = objectMapper.readValue(callbacksByName.get(toolName).getToolDefinition().inputSchema(), Map.class);
            var properties = (Map<?, ?>) schema.get("properties");
            assertFalse(properties.containsKey("reportId"));
        }
    }

    @Test
    void shouldReturnCurrentReportFromHiddenReportId() {
        var store = new CopilotReportSessionStore();
        var report = report("report-1");
        store.register(report);
        var tools = new CopilotReportTools(store);

        var result = tools.getCurrentReport("Potrzebuje aktualnego raportu.", toolContext());

        assertEquals("ok", result.status());
        assertEquals("report-1", result.reportId());
        assertEquals("flow-explorer", result.reportFeature());
        assertEquals(report, result.report());
    }

    @Test
    void shouldReturnMissingReportWhenHiddenReportIdIsMissing() {
        var tools = new CopilotReportTools(new CopilotReportSessionStore());

        var result = tools.getCurrentReport("Brak scope.", new ToolContext(Map.of()));

        assertEquals("missing_report", result.status());
        assertEquals("No active reportId is available in hidden ToolContext.", result.message());
    }

    @Test
    void shouldUpsertAllowedSection() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1"));
        var tools = new CopilotReportTools(store);

        var result = tools.upsertSection(
                "OVERVIEW",
                "Overview",
                1,
                "Updated overview.",
                AnalysisReportMeta.empty(),
                "Zapis sekcji.",
                toolContext()
        );

        assertEquals("ok", result.status());
        assertEquals(List.of("OVERVIEW"), result.updatedSectionIds());
        assertEquals("Updated overview.", store.current("report-1").orElseThrow().sections().get(0).markdown());
    }

    @Test
    void shouldRejectSectionOutsideHiddenAllowedList() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1"));
        var tools = new CopilotReportTools(store);

        var result = tools.upsertSection(
                "TECHNICAL_HANDOFF",
                "Technical handoff",
                2,
                "Technical notes.",
                null,
                "Nie ta sekcja.",
                toolContext()
        );

        assertEquals("rejected", result.status());
        assertEquals("Report section id is not allowed for this session.", result.message());
        assertEquals(List.of(), store.current("report-1").orElseThrow().sections());
    }

    @Test
    void shouldUpdateReportMeta() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1"));
        var tools = new CopilotReportTools(store);
        var meta = new AnalysisReportMeta(
                List.of(new AnalysisReportReference("evidence", "Logs", "elastic/logs", "Initial logs")),
                List.of("Limited log window."),
                List.of("Czy downstream odpowiada?"),
                List.of("No DB read."),
                "medium",
                List.of("Coverage warning.")
        );

        var result = tools.updateMeta(meta, "Aktualizacja metadanych.", toolContext());

        assertEquals("ok", result.status());
        assertEquals(meta, result.report().meta());
        assertEquals(meta, store.current("report-1").orElseThrow().meta());
    }

    @Test
    void shouldUpdateReportHeader() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1"));
        var tools = new CopilotReportTools(store);

        var result = tools.updateHeader(
                "DOWNSTREAM_TIMEOUT",
                "Profil klienta CRM | CRM Customer Context | CRM Customer Team",
                "Downstream timeout blocks customer settlement.",
                "Aktualizacja problemu wykrytego.",
                toolContext()
        );

        assertEquals("ok", result.status());
        assertEquals("DOWNSTREAM_TIMEOUT", result.report().header());
        assertEquals("Profil klienta CRM | CRM Customer Context | CRM Customer Team", result.report().subHeader());
        assertEquals("Downstream timeout blocks customer settlement.", result.report().markdownSummary());
        assertEquals("DOWNSTREAM_TIMEOUT", store.current("report-1").orElseThrow().header());
    }

    @Test
    void shouldRejectBlankReportHeader() {
        var store = new CopilotReportSessionStore();
        store.register(report("report-1"));
        var tools = new CopilotReportTools(store);

        var result = tools.updateHeader(
                " ",
                null,
                null,
                "Pusty header.",
                toolContext()
        );

        assertEquals("rejected", result.status());
        assertEquals("Report header must not be blank.", result.message());
        assertEquals("Header", store.current("report-1").orElseThrow().header());
    }

    private AnalysisReport report(String reportId) {
        return new AnalysisReport(
                reportId,
                "Header",
                "Sub header",
                "Summary",
                List.of(),
                AnalysisReportMeta.empty()
        );
    }

    private ToolContext toolContext() {
        return new ToolContext(Map.of(
                AgentToolContextKeys.REPORT_ID, "report-1",
                AgentToolContextKeys.REPORT_FEATURE, "flow-explorer",
                AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS, List.of("OVERVIEW", "FUNCTIONAL_FLOW")
        ));
    }
}
