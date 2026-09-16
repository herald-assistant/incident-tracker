package pl.mkn.tdw.features.uxinspector.job;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.features.uxinspector.ai.*;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureNormalizer;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetEvidenceMapper;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolver;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.features.uxinspector.job.api.*;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.features.uxinspector.job.localworkspace.UxInspectorLocalRunPersistence;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.report.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorJobServiceTest {

    @Test
    void shouldPersistQueuedBeforeDispatchAndCompleteFocusedRun() {
        var tasks = new ArrayList<Runnable>();
        var persistence = mock(UxInspectorLocalRunPersistence.class);
        var service = service(tasks::add, persistence);

        var accepted = service.startJob(request());

        assertThat(accepted.status()).isEqualTo(UxInspectorJobStatus.QUEUED);
        var snapshots = ArgumentCaptor.forClass(UxInspectorJobStateSnapshot.class);
        verify(persistence).persistRunSnapshot(snapshots.capture());
        assertThat(snapshots.getValue().status()).isEqualTo(UxInspectorJobStatus.QUEUED);
        assertThat(tasks).singleElement();

        tasks.get(0).run();

        var completed = service.getJob(accepted.jobId());
        assertThat(completed.status()).isEqualTo(UxInspectorJobStatus.COMPLETED);
        assertThat(completed.report().sections()).singleElement()
                .extracting(AnalysisReportSection::id).isEqualTo("answer");
        assertThat(completed.exportAvailable()).isTrue();
        verify(persistence, atLeast(7)).persistRunSnapshot(any());
    }

    @Test
    void shouldFailClosedBeforeDispatchWhenQueuedHistoryCannotBeCreated() {
        var executor = mock(TaskExecutor.class);
        var persistence = mock(UxInspectorLocalRunPersistence.class);
        doThrow(new IllegalStateException("Synthetic CRM store unavailable"))
                .when(persistence).persistRunSnapshot(any());
        var service = service(executor, persistence);

        assertThatThrownBy(() -> service.startJob(request()))
                .isInstanceOf(UxInspectorJobException.class)
                .hasMessageContaining("QUEUED history");
        verifyNoInteractions(executor);
    }

    @Test
    void shouldKeepConcurrentRunsIsolated() {
        var tasks = new ArrayList<Runnable>();
        var persistence = mock(UxInspectorLocalRunPersistence.class);
        var service = service(tasks::add, persistence);

        var first = service.startJob(request());
        var second = service.startJob(request());

        assertThat(first.jobId()).isNotEqualTo(second.jobId());
        assertThat(first.status()).isEqualTo(UxInspectorJobStatus.QUEUED);
        assertThat(second.status()).isEqualTo(UxInspectorJobStatus.QUEUED);
        assertThat(tasks).hasSize(2);

        tasks.get(1).run();
        assertThat(service.getJob(second.jobId()).status()).isEqualTo(UxInspectorJobStatus.COMPLETED);
        assertThat(service.getJob(first.jobId()).status()).isEqualTo(UxInspectorJobStatus.QUEUED);

        tasks.get(0).run();
        assertThat(service.getJob(first.jobId()).status()).isEqualTo(UxInspectorJobStatus.COMPLETED);
        assertThat(service.getJob(first.jobId()).jobId()).isEqualTo(first.jobId());
        assertThat(service.getJob(second.jobId()).jobId()).isEqualTo(second.jobId());
    }

    private UxInspectorJobService service(TaskExecutor executor, UxInspectorLocalRunPersistence persistence) {
        var normalizer = mock(UxInspectorCaptureNormalizer.class);
        when(normalizer.normalize(any())).thenReturn(capture());
        var selection = mock(UxInspectorAiSelectionValidator.class);
        var resolver = mock(UxInspectorTargetResolver.class);
        when(resolver.resolve(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(targetContext());
        var evidence = mock(UxInspectorTargetEvidenceMapper.class);
        when(evidence.map(any())).thenReturn(List.of());
        var preparation = mock(UxInspectorPromptPreparationService.class);
        when(preparation.prepare(any(), any())).thenReturn(new UxInspectorPromptPreparation(
                "Focused CRM prompt", java.util.Map.of("target", "CRM target"),
                java.util.Set.of(SOURCE_PATH, TEMPLATE_PATH)));
        var provider = mock(UxInspectorAnalysisProvider.class);
        when(provider.analyze(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(completedAnalysis());
        var auth = mock(AnalysisAiAuthRefResolver.class);
        when(auth.resolveForCurrentRequest()).thenReturn(AnalysisAiAuthRef.localToken("CRM test"));
        return new UxInspectorJobService(normalizer, selection, resolver, evidence, preparation, provider,
                executor, auth, persistence);
    }

    private UxInspectorAiAnalysis completedAnalysis() {
        var context = targetContext();
        var reference = new AnalysisReportReference("source", "Target", TEMPLATE_PATH + "#L1", "CRM target");
        var meta = new AnalysisReportMeta(List.of(reference), List.of(), List.of(), List.of(), "high", List.of());
        var report = new AnalysisReport("report-crm", "UX Inspector", "CRM", "Zapis zalezy od walidacji.",
                List.of(new AnalysisReportSection("answer", "Odpowiedz", 1,
                        "Przycisk jest aktywny po poprawnym wypelnieniu formularza.", meta)), meta);
        var result = new UxInspectorResultResponse(capture().captureId(), "Zapisz kontakt", context.view(),
                context.sourceRevision(), context.status(), report.markdownSummary(), report.sections().get(0).markdown(),
                "high", List.of(reference), List.of(), List.of(), null);
        return new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.COMPLETED, result, report, null,
                "session-crm", List.of());
    }

    private UxInspectorJobStartRequest request() {
        return new UxInspectorJobStartRequest("crm-agent-portal", "main", VIEW_ID, REVISION,
                "Dlaczego przycisk jest zablokowany?", capture(), "gpt-crm", "medium");
    }
}
