package pl.mkn.tdw.features.incidentanalysis.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.report.CopilotIncidentReportMapper;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentFollowUpReportProjectionTest {
    @Test
    void shouldNormalizeJsonFallbackIntoEditableReport() {
        var mapper = new CopilotIncidentReportMapper();
        var projector = new IncidentFollowUpReportProjection(mapper);
        var result = new AnalysisResultResponse("COMPLETED", "crm-correlation", "test", "main",
                "CRM timeout", "Customer care", "CRM", "CRM team", "Customer path",
                "Technical handoff", "medium", List.of("No downstream logs"), "Initial prompt", null);

        var normalized = projector.normalize(result, null, "crm-run");

        assertEquals("incident-report-crm-run", normalized.reportId());
        assertTrue(mapper.tryMap(normalized, result.prompt(), result.usage(), null).isPresent());
        assertEquals("Customer path", normalized.sections().get(0).markdown());
    }
}
