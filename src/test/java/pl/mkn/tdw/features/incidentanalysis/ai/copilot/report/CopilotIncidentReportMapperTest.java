package pl.mkn.tdw.features.incidentanalysis.ai.copilot.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentReportSectionIds;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentReportMapperTest {

    private final CopilotIncidentReportMapper mapper = new CopilotIncidentReportMapper();

    @Test
    void shouldMapCompleteIncidentReportToInitialAnalysisResponse() {
        var response = mapper.tryMap(
                report(),
                "Prompt body",
                null,
                "session-1"
        ).orElseThrow();

        assertEquals("copilot-sdk", response.providerName());
        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals("Rozliczenie klienta", response.affectedProcess());
        assertEquals("Catalog Context", response.affectedBoundedContext());
        assertEquals("Core Integration Team", response.affectedTeam());
        assertEquals("Functional report section.", response.functionalAnalysis());
        assertEquals("Technical handoff section.", response.technicalAnalysis());
        assertEquals("medium", response.confidence());
        assertEquals(List.of(
                "Limited log window.",
                "Gap: No DB evidence.",
                "Warning: Coverage warning.",
                "Functional visibility limit.",
                "Gap: Technical gap."
        ), response.visibilityLimits());
        assertEquals("Prompt body", response.prompt());
        assertEquals("session-1", response.copilotSessionId());
        assertEquals("report-1", response.report().reportId());
    }

    @Test
    void shouldRejectReportWithoutRequiredSections() {
        var incomplete = new AnalysisReport(
                "report-1",
                "DOWNSTREAM_TIMEOUT",
                "Incident",
                "",
                List.of(new AnalysisReportSection(
                        CopilotIncidentReportSectionIds.FUNCTIONAL_ANALYSIS,
                        "Functional analysis",
                        1,
                        "Functional report section.",
                        AnalysisReportMeta.empty()
                )),
                AnalysisReportMeta.empty()
        );

        assertTrue(mapper.tryMap(incomplete, "Prompt body", null, "session-1").isEmpty());
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "report-1",
                "DOWNSTREAM_TIMEOUT",
                "Incident",
                "",
                List.of(
                        new AnalysisReportSection(
                                CopilotIncidentReportSectionIds.FUNCTIONAL_ANALYSIS,
                                "Functional analysis",
                                1,
                                "Functional report section.",
                                new AnalysisReportMeta(
                                        List.of(),
                                        List.of("Functional visibility limit."),
                                        List.of(),
                                        List.of(),
                                        null,
                                        List.of()
                                )
                        ),
                        new AnalysisReportSection(
                                CopilotIncidentReportSectionIds.TECHNICAL_HANDOFF,
                                "Technical handoff",
                                2,
                                "Technical handoff section.",
                                new AnalysisReportMeta(
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of("Technical gap."),
                                        null,
                                        List.of()
                                )
                        )
                ),
                new AnalysisReportMeta(
                        List.of(
                                new AnalysisReportReference("process", "Rozliczenie klienta", null, null),
                                new AnalysisReportReference("boundedContext", "Catalog Context", null, null),
                                new AnalysisReportReference("team", "Core Integration Team", null, null)
                        ),
                        List.of("Limited log window."),
                        List.of("Czy downstream jest stabilny?"),
                        List.of("No DB evidence."),
                        "medium",
                        List.of("Coverage warning.")
                )
        );
    }
}
