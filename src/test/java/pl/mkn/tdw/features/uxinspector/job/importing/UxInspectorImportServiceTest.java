package pl.mkn.tdw.features.uxinspector.job.importing;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAiAnalysis;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAiAnalysisStatus;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureNormalizer;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.features.uxinspector.job.localworkspace.UxInspectorLocalRunPersistence;
import pl.mkn.tdw.features.uxinspector.job.state.UxInspectorJobState;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.report.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorImportServiceTest {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final LocalWorkspaceProperties properties = new LocalWorkspaceProperties();
    private final UxInspectorLocalRunPersistence persistence = mock(UxInspectorLocalRunPersistence.class);
    private final UxInspectorImportService service = new UxInspectorImportService(objectMapper, properties, persistence,
            new UxInspectorCaptureNormalizer(objectMapper));

    @Test
    void shouldImportOnlyStrictUxInspectorV1AsReadOnlyHistoricalResult() {
        var document = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));

        var imported = service.importReadOnly(document);

        assertThat(imported.jobId()).startsWith("ux-inspector-import-");
        assertThat(imported.report().sections()).singleElement().extracting(AnalysisReportSection::id)
                .isEqualTo("answer");
        assertThat(imported.exportAvailable()).isTrue();
        verify(persistence).persistRunSnapshot(any());
    }

    @Test
    void shouldRejectUiExplorerSchemaUnknownVersionUnknownFieldsAndCaptureV3() {
        ObjectNode uiExplorer = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        uiExplorer.put("schema", "tdw.ui-explorer-export");
        ObjectNode future = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        future.put("version", 99);
        ObjectNode unknown = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        unknown.put("legacy", true);
        ObjectNode unknownNested = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        unknownNested.withObject("/payload/job").put("legacy", true);
        ObjectNode captureV3 = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        captureV3.withObject("/payload/job/request/capture").put("version", 3);
        ObjectNode nonCanonicalCapture = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        nonCanonicalCapture.withObject("/payload/job/request/capture/target/domFingerprint/stableAttributes")
                .put("data-testid", "secret-token");
        ObjectNode mismatchedResult = objectMapper.valueToTree(UxInspectorExportEnvelope.from(completedSnapshot(), Instant.now()));
        mismatchedResult.withObject("/payload/job/result").put("captureId", "cap_other_crm_target");

        assertInvalid(uiExplorer);
        assertInvalid(future);
        assertInvalid(unknown);
        assertInvalid(unknownNested);
        assertInvalid(captureV3);
        assertInvalid(nonCanonicalCapture);
        assertInvalid(mismatchedResult);
    }

    private void assertInvalid(ObjectNode document) {
        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(UxInspectorJobException.class);
    }

    private pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot completedSnapshot() {
        var request = new UxInspectorJobStartRequest("crm-agent-portal", "main", VIEW_ID, REVISION,
                "Jak dziala zapis kontaktu?", capture(), "gpt-crm", "medium");
        var state = new UxInspectorJobState("ux-crm-completed", request);
        state.tryStart();
        state.targetResolved(targetContext(), List.of());
        state.preparationStarted();
        state.preparationCompleted("Focused CRM prompt");
        state.analysisStarted();
        var reference = new AnalysisReportReference("source", "Target", TEMPLATE_PATH + "#L1", "CRM target");
        var meta = new AnalysisReportMeta(List.of(reference), List.of(), List.of(), List.of(), "high", List.of());
        var report = new AnalysisReport("report-crm", "UX Inspector", "CRM", "Zapis zalezy od walidacji.",
                List.of(new AnalysisReportSection("answer", "Odpowiedz", 1, "Odpowiedz CRM.", meta)), meta);
        var result = new UxInspectorResultResponse(capture().captureId(), "Zapisz kontakt", targetContext().view(),
                targetContext().sourceRevision(), targetContext().status(), report.markdownSummary(), "Odpowiedz CRM.",
                "high", List.of(reference), List.of(), List.of(), null);
        state.analysisCompleted(new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.COMPLETED, result, report,
                null, "session-crm", List.of()));
        return state.snapshot();
    }
}
