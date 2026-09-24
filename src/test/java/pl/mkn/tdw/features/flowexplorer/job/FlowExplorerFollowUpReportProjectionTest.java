package pl.mkn.tdw.features.flowexplorer.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportMapper;
import pl.mkn.tdw.features.flowexplorer.job.api.*;
import pl.mkn.tdw.shared.ai.report.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowExplorerFollowUpReportProjectionTest {
    @Test
    void shouldProjectCorrectedSectionAndPreserveRunMetadata() {
        var mapper = new FlowExplorerReportMapper();
        var projector = new FlowExplorerFollowUpReportProjection(mapper);
        var modes = List.of(new FlowExplorerResultSectionModeAssignment(
                FlowExplorerResultSectionId.FUNCTIONAL_FLOW, "Functional flow", FlowExplorerResultSectionMode.DEEP));
        var current = report("Initial CRM path");
        var initial = mapper.tryMap(current, FlowExplorerAnalysisGoal.DEEP_DISCOVERY, modes).orElseThrow();
        var previous = new FlowExplorerResultResponse("COMPLETED", "crm-service", "crm-read", "GET",
                "/customers/{id}", "main", FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "Initial prompt", initial, null);
        var changed = report("Corrected CRM path");

        var projected = projector.project(previous, current, changed, modes);

        assertEquals("Corrected CRM path", projected.aiResponse().sections().get(0).markdown());
        assertEquals(previous.prompt(), projected.prompt());
        assertEquals(previous.status(), projected.status());
        assertSame(previous, projector.project(previous, current, current, modes));
    }

    @Test
    void shouldNormalizeJsonFallbackBeforeFirstFollowUpEdit() {
        var mapper = new FlowExplorerReportMapper();
        var projector = new FlowExplorerFollowUpReportProjection(mapper);
        var modes = List.of(new FlowExplorerResultSectionModeAssignment(
                FlowExplorerResultSectionId.FUNCTIONAL_FLOW, "Functional flow", FlowExplorerResultSectionMode.DEEP));
        var response = new pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY, "business_or_system_analyst_tester",
                new FlowExplorerResultOverview("CRM overview", "medium", List.of()),
                List.of(new FlowExplorerResultSection(FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                        "Functional flow", FlowExplorerResultSectionMode.DEEP, "CRM path", List.of(),
                        List.of(), List.of())), List.of(), List.of(), List.of(), "medium");
        var result = new FlowExplorerResultResponse("COMPLETED", "crm-service", "crm-read", "GET",
                "/customers/{id}", "main", FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "Initial prompt", response, null);

        var normalized = projector.normalize(result, null, modes, "crm-run");

        assertEquals("flow-explorer-report-crm-run", normalized.reportId());
        assertTrue(mapper.tryMap(normalized, result.goal(), modes).isPresent());
    }

    private AnalysisReport report(String body) {
        return new AnalysisReport("flow-report-crm", "CRM flow", "main", "CRM overview",
                List.of(new AnalysisReportSection("OVERVIEW", "Overview", 0, "CRM overview", AnalysisReportMeta.empty()),
                        new AnalysisReportSection("FUNCTIONAL_FLOW", "Functional flow", 1, body,
                                AnalysisReportMeta.empty())), AnalysisReportMeta.empty());
    }
}
