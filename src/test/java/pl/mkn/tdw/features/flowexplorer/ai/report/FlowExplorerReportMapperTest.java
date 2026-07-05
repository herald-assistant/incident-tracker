package pl.mkn.tdw.features.flowexplorer.ai.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerReportMapperTest {

    private final FlowExplorerReportMapper mapper = new FlowExplorerReportMapper();

    @Test
    void shouldMapCompleteReportToFlowExplorerResponse() {
        var response = mapper.tryMap(
                report(),
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                FlowExplorerResultSectionModeResolver.resolve(List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW))
        ).orElseThrow();

        assertEquals(FlowExplorerAnalysisGoal.DEEP_DISCOVERY, response.goal());
        assertEquals("Tester chce poznac GET /api/customers/{id}.", response.overview().markdown());
        assertEquals("high", response.overview().confidence());
        assertEquals(List.of("crm-service:CustomerProfileController.java:L12-L24"), response.overview().sourceRefs());
        assertEquals("high", response.confidence());
        assertEquals(List.of("crm-service:CustomerProfileController.java:L12-L24"), response.sourceReferences());
        assertEquals(List.of("Nie widac konfiguracji runtime."), response.globalVisibilityLimits());
        assertEquals(List.of("Czy endpoint ma wariant dla klienta nieaktywnego?"), response.globalOpenQuestions());
        assertEquals(4, response.sections().size());
        assertEquals(FlowExplorerResultSectionId.FUNCTIONAL_FLOW, response.sections().get(0).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(0).mode());
        assertEquals("Flow krok po kroku.", response.sections().get(0).markdown());
        assertEquals(List.of("Gap: Brak glossary dla terminu klient aktywny."), response.sections().get(0).visibilityLimits());
    }

    @Test
    void shouldRejectIncompleteReport() {
        var incompleteReport = new AnalysisReport(
                "report-1",
                "Header",
                "Sub header",
                "",
                List.of(new AnalysisReportSection(
                        FlowExplorerReportSectionIds.OVERVIEW,
                        "Overview",
                        0,
                        "Only overview.",
                        AnalysisReportMeta.empty()
                )),
                AnalysisReportMeta.empty()
        );

        assertTrue(mapper.tryMap(
                incompleteReport,
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                FlowExplorerResultSectionModeResolver.resolve(List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW))
        ).isEmpty());
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "report-1",
                "Header",
                "Sub header",
                "",
                List.of(
                        new AnalysisReportSection(
                                FlowExplorerReportSectionIds.OVERVIEW,
                                "Overview",
                                0,
                                "Tester chce poznac GET /api/customers/{id}.",
                                meta("high")
                        ),
                        section(FlowExplorerResultSectionId.FUNCTIONAL_FLOW, "Flow krok po kroku.", metaWithGap()),
                        section(FlowExplorerResultSectionId.VALIDATIONS, "id jest wymagane.", AnalysisReportMeta.empty()),
                        section(FlowExplorerResultSectionId.PERSISTENCE, "Repository pobiera klienta po id.", AnalysisReportMeta.empty()),
                        section(FlowExplorerResultSectionId.INTEGRATIONS, "Brak integracji zewnetrznej.", AnalysisReportMeta.empty())
                ),
                new AnalysisReportMeta(
                        List.of(reference()),
                        List.of("Nie widac konfiguracji runtime."),
                        List.of("Czy endpoint ma wariant dla klienta nieaktywnego?"),
                        List.of(),
                        "high",
                        List.of()
                )
        );
    }

    private AnalysisReportSection section(
            FlowExplorerResultSectionId sectionId,
            String markdown,
            AnalysisReportMeta meta
    ) {
        return new AnalysisReportSection(
                sectionId.name(),
                sectionId.title(),
                sectionId.ordinal() + 1,
                markdown,
                meta
        );
    }

    private AnalysisReportMeta meta(String confidence) {
        return new AnalysisReportMeta(
                List.of(reference()),
                List.of(),
                List.of(),
                List.of(),
                confidence,
                List.of()
        );
    }

    private AnalysisReportMeta metaWithGap() {
        return new AnalysisReportMeta(
                List.of(),
                List.of(),
                List.of(),
                List.of("Brak glossary dla terminu klient aktywny."),
                null,
                List.of()
        );
    }

    private AnalysisReportReference reference() {
        return new AnalysisReportReference(
                "code",
                "CustomerController",
                "crm-service:CustomerProfileController.java:L12-L24",
                "Endpoint handler"
        );
    }
}
