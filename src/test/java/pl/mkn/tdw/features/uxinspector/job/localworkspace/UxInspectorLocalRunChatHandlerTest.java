package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.ai.*;
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
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorLocalRunChatHandlerTest {
    @Test
    void shouldRequireExplicitContinuationForCurrentRun() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var handler = new UxInspectorLocalRunChatHandler(mapper, mock(pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver.class),
                mock(pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpPromptService.class),
                mock(pl.mkn.tdw.features.uxinspector.ai.chat.UxInspectorFollowUpChatService.class),
                new pl.mkn.tdw.features.uxinspector.job.UxInspectorFollowUpReportProjection(new pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper()),
                mock(pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper.class),
                mock(pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver.class));
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
