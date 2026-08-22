package pl.mkn.tdw.features.uiexplorer.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.EMBEDDED_COMPONENT_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.FETCHED_VALIDATOR_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.completeReport;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerReportMapperTest {

    private final UiExplorerReportMapper mapper = new UiExplorerReportMapper();

    @Test
    void shouldMapCompleteToolBuiltCrmReportWithoutReadingAssistantResponse() {
        var mapping = mapper.map(
                completeReport("ui-explorer-report-crm-map-1", EMBEDDED_COMPONENT_PATH),
                request(),
                context(),
                Set.of(),
                null
        );

        assertThat(mapping.complete()).isTrue();
        assertThat(mapping.limitations()).isEmpty();
        assertThat(mapping.result().screen()).isEqualTo(context().screen());
        assertThat(mapping.result().sourceRevision()).isEqualTo(context().sourceRevision());
        assertThat(mapping.result().sections())
                .extracting(section -> section.coverage())
                .containsOnly(UiExplorerCoverageStatus.READY);
        assertThat(mapping.result().sections())
                .extracting(section -> section.confidence())
                .containsOnly(UiExplorerClaimConfidence.CONFIRMED);
        assertThat(mapping.report().header()).isEqualTo("/contacts/:contactId/preferences");
        assertThat(mapping.report().subHeader()).isEqualTo("CRM Contact Preferences");
    }

    @Test
    void shouldAcceptOnlyCrmSourceCapturedByDeterministicOrToolEvidence() {
        var sourceReport = completeReport("ui-explorer-report-crm-map-2", FETCHED_VALIDATOR_PATH);

        var rejected = mapper.map(sourceReport, request(), context(), Set.of(), null);
        var accepted = mapper.map(sourceReport, request(), context(), Set.of(FETCHED_VALIDATOR_PATH), null);

        assertThat(rejected.complete()).isFalse();
        assertThat(rejected.limitations()).anyMatch(value -> value.contains("unverified source reference"));
        assertThat(rejected.report().sections())
                .flatExtracting(section -> section.meta().references())
                .isEmpty();
        assertThat(accepted.complete()).isTrue();
        assertThat(accepted.result().sections())
                .flatExtracting(section -> section.sourceReferences())
                .extracting(reference -> reference.path())
                .containsOnly(FETCHED_VALIDATOR_PATH);
    }

    @Test
    void shouldPreserveWrittenCrmSectionAndExposeMissingActiveSectionAsPartial() {
        var complete = completeReport("ui-explorer-report-crm-map-3", EMBEDDED_COMPONENT_PATH);
        var onlyOverview = new AnalysisReport(
                complete.reportId(),
                complete.header(),
                complete.subHeader(),
                complete.markdownSummary(),
                List.of(complete.sections().get(0)),
                complete.meta()
        );

        var mapping = mapper.map(onlyOverview, request(), context(), Set.of(), null);

        assertThat(mapping.complete()).isFalse();
        assertThat(mapping.result().sections()).hasSize(2);
        assertThat(mapping.result().sections().get(0).markdown()).isNotBlank();
        assertThat(mapping.result().sections().get(1).coverage()).isEqualTo(UiExplorerCoverageStatus.BLOCKED);
        assertThat(mapping.report().sections().get(1).meta().gaps())
                .contains("AI did not save this active report section through report tools.");
        assertThat(mapping.limitations()).contains("Active report section was not saved: FORMS_AND_RULES.");
    }

    @Test
    void shouldRemoveCrmReferenceWithUnsafeLineRangeWithoutFailingTheReport() {
        var complete = completeReport("ui-explorer-report-crm-map-4", EMBEDDED_COMPONENT_PATH);
        var unsafeMeta = new AnalysisReportMeta(
                List.of(new AnalysisReportReference(
                        "source",
                        "CRM contact component",
                        EMBEDDED_COMPONENT_PATH + "#L999999999999999999999-L1",
                        "Synthetic CRM source"
                )),
                List.of(), List.of(), List.of(), "high", List.of()
        );
        var unsafeSection = new AnalysisReportSection(
                complete.sections().get(0).id(),
                complete.sections().get(0).title(),
                complete.sections().get(0).order(),
                complete.sections().get(0).markdown(),
                unsafeMeta
        );
        var report = new AnalysisReport(
                complete.reportId(), complete.header(), complete.subHeader(), complete.markdownSummary(),
                List.of(unsafeSection, complete.sections().get(1)), complete.meta()
        );

        var mapping = mapper.map(report, request(), context(), Set.of(), null);

        assertThat(mapping.complete()).isFalse();
        assertThat(mapping.result().sections().get(0).sourceReferences()).isEmpty();
        assertThat(mapping.limitations()).anyMatch(value -> value.contains("unverified source reference"));
    }
}
