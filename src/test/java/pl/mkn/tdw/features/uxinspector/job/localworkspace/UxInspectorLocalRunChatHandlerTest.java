package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.features.uxinspector.ai.*;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatRequest;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatService;
import pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpPromptService;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorFollowUpReportProjection;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorAnalysisRequest;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.features.uxinspector.job.state.UxInspectorJobState;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.report.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorLocalRunChatHandlerTest {
    @Test
    void restoredRunUsesTheSameFollowUpResearchAndOriginalSnapshots() {
        var mapper = JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var snapshot = completedSnapshot("ux-crm-contacts");
        var continuation = new LocalAnalysisRunContinuation(true, "CRM", null, null, "crm-session", null, null);
        var record = LocalAnalysisRunRecord.v1(mapper.valueToTree(UxInspectorExportEnvelope.from(snapshot, Instant.now())), continuation)
                .withStoreSnapshot(new LocalAnalysisRunStoreSnapshot(mapper.createObjectNode(), Instant.EPOCH, "CRM", 0))
                .withHttpHistorySnapshot(pl.mkn.tdw.common.HttpHistoryTestFixtures.snapshot(mapper.createArrayNode()));
        var index = new LocalAnalysisRunIndexEntry(snapshot.jobId(), LocalAnalysisRunRecord.SCHEMA, 1,
                "runs/ux-crm-contacts/run.json", "ux-inspector", "CRM Contacts", "COMPLETED",
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt());
        var resolver = mock(UxInspectorTargetResolver.class);
        when(resolver.resolve("crm-agent-portal", "main", VIEW_ID, capture())).thenReturn(targetContext());
        var chat = mock(UxInspectorFollowUpChatService.class);
        when(chat.chat(any(), any(), any())).thenReturn(new CopilotExecutionResult(
                "Backend sprawdza numer telefonu.", null, "crm-session", snapshot.report()));
        var handler = new UxInspectorLocalRunChatHandler(mapper,
                pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureTestSupport.envelopes(mapper),
                resolver, new UxInspectorFollowUpPromptService(), chat, new UxInspectorFollowUpReportProjection(new UxInspectorReportMapper()),
                new CopilotRunAuthMapper(), mock(CopilotAccessTokenResolver.class));

        var result = handler.continueRun(index, record, "Dlaczego backend odrzuca numer telefonu?");

        var request = ArgumentCaptor.forClass(UxInspectorFollowUpChatRequest.class);
        verify(chat).chat(request.capture(), any(), any());
        assertThat(request.getValue().copilotSessionId()).isEqualTo("crm-session");
        assertThat(request.getValue().storeRunId()).isEqualTo(snapshot.jobId());
        assertThat(request.getValue().context().sourceScope().mainGroup()).isEqualTo("CRM");
        assertThat(result.record().httpHistorySnapshot()).isEqualTo(record.httpHistorySnapshot());
        assertThat(result.record().storeSnapshot()).isEqualTo(record.storeSnapshot());
        assertThat(result.record().exportEnvelope().at("/payload/job/report")).isEqualTo(record.exportEnvelope().at("/payload/job/report"));
        assertThat(result.record().exportEnvelope().at("/payload/job/chatMessages/1/content").asText()).isEqualTo("Backend sprawdza numer telefonu.");
    }

    @Test
    void restoresValidCaptureAndRejectsCorruptCaptureInContinuationAndStoredExport() {
        var mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var handler = handler(mapper);
        var snapshot = completedSnapshot("ux-crm-restored");
        var continuation = new LocalAnalysisRunContinuation(true, "CRM", null, null, "crm-session", null, null);
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(UxInspectorExportEnvelope.from(snapshot, Instant.now()));
        var record = LocalAnalysisRunRecord.v1(document, continuation);
        var index = new LocalAnalysisRunIndexEntry(snapshot.jobId(), LocalAnalysisRunRecord.SCHEMA, 1,
                "runs/ux-crm-restored/run.json", "ux-inspector", "CRM target", "COMPLETED",
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt());
        assertThat(handler.canContinue(index, record)).isTrue();

        var jobs = mock(pl.mkn.tdw.features.uxinspector.job.UxInspectorJobService.class);
        org.mockito.Mockito.when(jobs.getJob(snapshot.jobId())).thenThrow(new pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException(
                "UX_INSPECTOR_JOB_NOT_FOUND", pl.mkn.tdw.shared.error.UserFacingErrorType.NOT_FOUND, "CRM run is saved only"));
        var runs = mock(LocalAnalysisRunStore.class);
        var exports = new pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportService(
                pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureTestSupport.envelopes(mapper), jobs, runs);
        org.mockito.Mockito.when(runs.findById(snapshot.jobId())).thenReturn(java.util.Optional.of(record));
        assertThat(exports.export(snapshot.jobId()).payload().job().request().capture()).isEqualTo(capture());

        for (var invalid : List.of("coercion", "missing", "unknown", "null")) {
            var broken = document.deepCopy();
            var state = broken.withObject("/payload/job/request/capture/target/state");
            switch (invalid) {
                case "coercion" -> state.put("checked", "false");
                case "missing" -> state.remove("checked");
                case "unknown" -> state.put("extra", true);
                case "null" -> state.putNull("disabled");
            }
            var corrupted = LocalAnalysisRunRecord.v1(broken, continuation);
            assertThat(handler.canContinue(index, corrupted)).isFalse();
            org.mockito.Mockito.when(runs.findById(snapshot.jobId())).thenReturn(java.util.Optional.of(corrupted));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> exports.export(snapshot.jobId()))
                    .isInstanceOf(pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException.class);
        }
    }

    @Test
    void shouldRequireExplicitContinuationForCurrentRun() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var handler = handler(mapper);
        var snapshot = completedSnapshot("ux-crm-legacy");
        var record = LocalAnalysisRunRecord.v1(mapper.valueToTree(new UxInspectorExportEnvelope(
                        UxInspectorExportEnvelope.SCHEMA, UxInspectorExportEnvelope.VERSION, Instant.now(),
                        new UxInspectorExportEnvelope.Payload(UxInspectorExportEnvelope.PAYLOAD_TYPE,
                                UxInspectorExportEnvelope.RESULT_CONTRACT, snapshot))),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null));
        var entry = new LocalAnalysisRunIndexEntry("ux-crm-legacy", LocalAnalysisRunRecord.SCHEMA, 1,
                "runs/ux-crm-legacy/run.json", "ux-inspector", "CRM target", "COMPLETED",
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt());

        assertThat(handler.canContinue(entry, record)).isFalse();
    }

    private UxInspectorLocalRunChatHandler handler(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new UxInspectorLocalRunChatHandler(mapper, pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureTestSupport.envelopes(mapper), mock(pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver.class),
                mock(pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpPromptService.class),
                mock(pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatService.class),
                new pl.mkn.tdw.features.uxinspector.job.UxInspectorFollowUpReportProjection(new pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper()),
                mock(pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper.class),
                mock(pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver.class));
    }

    private pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot completedSnapshot(String id) {
        var state = new UxInspectorJobState(id, new UxInspectorAnalysisRequest("crm-agent-portal", "main", VIEW_ID,
                "Jak działa zapis?", capture(), "gpt-crm", "medium"));
        state.tryStart(); state.targetResolved(targetContext(), List.of()); state.preparationStarted();
        state.preparationCompleted("CRM prompt", 1); state.analysisStarted();
        var meta = AnalysisReportMeta.empty();
        var report = new AnalysisReport("ux-inspector-report-" + id, "UX Inspector", "CRM", "Zapis kontaktu",
                List.of(new AnalysisReportSection("answer", "Odpowiedź", 1, "Pole jest wymagane.", meta)), meta);
        var result = new UxInspectorResultResponse(capture().captureId(), "Zapisz kontakt", targetContext().view(),
                targetContext().sourceRevision(), targetContext().status(), report.markdownSummary(), "Pole jest wymagane.",
                "high", List.of(), List.of(), List.of(), null);
        state.analysisCompleted(new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.COMPLETED, result, report,
                null, "ux-inspector-" + id, List.of()));
        return state.snapshot();
    }
}
