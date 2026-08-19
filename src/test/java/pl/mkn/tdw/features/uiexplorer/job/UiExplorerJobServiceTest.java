package pl.mkn.tdw.features.uiexplorer.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysis;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysisStatus;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAnalysisProvider;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.FETCHED_VALIDATOR_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.fetchedCodeEvidence;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerJobServiceTest {

    @Test
    void shouldReturnQueuedImmediatelyAndPublishCompletedCrmAnalysisAfterWorkerRunsOnce() {
        var sourceContextService = UiExplorerJobServiceTestCreator.sourceContextService(context());
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        var usage = usage();
        var result = result(usage);
        var report = UiExplorerJobServiceTestCreator.report("crm-completed-report", result);
        doAnswer(invocation -> {
            invocation.<pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener>getArgument(5)
                    .onToolEvidenceUpdated(fetchedCodeEvidence(FETCHED_VALIDATOR_PATH));
            invocation.<pl.mkn.tdw.shared.ai.AnalysisAiActivityListener>getArgument(6)
                    .onAiActivity(activity());
            return new UiExplorerAiAnalysis(
                    UiExplorerAiAnalysisStatus.COMPLETED,
                    result,
                    usage,
                    "raw synthetic CRM prompt",
                    "crm-copilot-session",
                    report,
                    List.of()
            );
        }).when(analysisProvider).analyze(any(), any(), any(), any(), any(), any(), any());
        var executor = new CapturingTaskExecutor();
        var localRunPersistence = mock(UiExplorerLocalRunPersistence.class);
        var service = UiExplorerJobServiceTestCreator.create(
                sourceContextService,
                analysisProvider,
                executor,
                localRunPersistence
        );

        var accepted = service.startJob(request());

        assertThat(accepted.status()).isEqualTo(UiExplorerJobStatus.QUEUED);
        assertThat(accepted.completedAt()).isNull();
        assertThat(accepted.preparedPrompt()).isNull();
        assertThat(accepted.result()).isNull();
        assertThat(accepted.report()).isNull();
        assertThat(accepted.outputAvailability().code()).isEqualTo("UI_EXPLORER_ANALYSIS_IN_PROGRESS");
        assertThat(accepted.steps()).extracting(step -> step.code() + ":" + step.status())
                .containsExactly(
                        "SCREEN_DISCOVERY:PENDING",
                        "SOURCE_CONTEXT:PENDING",
                        "AI_PREPARATION:PENDING",
                        "AI_ANALYSIS:PENDING"
                );

        executor.runCapturedTwice();
        var completed = service.getJob(accepted.jobId());

        assertThat(completed.status()).isEqualTo(UiExplorerJobStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.request().systemLabel()).isEqualTo("CRM Agent Portal");
        assertThat(completed.steps()).extracting(step -> step.code() + ":" + step.status())
                .containsExactly(
                        "SCREEN_DISCOVERY:COMPLETED",
                        "SOURCE_CONTEXT:PARTIAL",
                        "AI_PREPARATION:COMPLETED",
                        "AI_ANALYSIS:COMPLETED"
                );
        assertThat(completed.steps()).filteredOn(step -> "AI_ANALYSIS".equals(step.code()))
                .singleElement().extracting(step -> step.usage()).isEqualTo(usage);
        assertThat(completed.contextSections()).isNotEmpty();
        assertThat(completed.contextSections()).extracting(section -> section.category())
                .contains("selected-screen", "source-manifest", "ai-artifacts");
        assertThat(completed.steps()).filteredOn(step -> "SCREEN_DISCOVERY".equals(step.code()))
                .singleElement().satisfies(step -> assertThat(step.producesEvidence())
                        .extracting(reference -> reference.category())
                        .containsExactly("selected-screen"));
        assertThat(completed.steps()).filteredOn(step -> "SOURCE_CONTEXT".equals(step.code()))
                .singleElement().satisfies(step -> {
                    assertThat(step.consumesEvidence()).extracting(reference -> reference.category())
                            .containsExactly("selected-screen");
                    assertThat(step.producesEvidence()).extracting(reference -> reference.category())
                            .contains("source-manifest", "technical-signals", "section-coverage", "source-boundary");
                });
        assertThat(completed.steps()).filteredOn(step -> "AI_PREPARATION".equals(step.code()))
                .singleElement().satisfies(step -> assertThat(step.producesEvidence())
                        .extracting(reference -> reference.category())
                        .containsExactly("ai-artifacts"));
        assertThat(completed.steps()).filteredOn(step -> "AI_ANALYSIS".equals(step.code()))
                .singleElement().satisfies(step -> assertThat(step.consumesEvidence())
                        .extracting(reference -> reference.category())
                        .containsExactly("ai-artifacts"));
        assertThat(completed.toolEvidenceSections()).containsExactly(fetchedCodeEvidence(FETCHED_VALIDATOR_PATH));
        assertThat(completed.aiActivityEvents()).extracting(AnalysisAiActivityEvent::eventId)
                .containsExactly("crm-ai-event-1");
        assertThat(completed.usage()).isEqualTo(usage);
        assertThat(completed.result()).isEqualTo(result);
        assertThat(completed.report()).isEqualTo(report);
        assertThat(completed.preparedPrompt()).contains("UI Explorer canonical prompt");
        assertThat(completed.outputAvailability().status())
                .isEqualTo(UiExplorerOutputAvailabilityStatus.AVAILABLE);
        assertThat(completed.exportAvailable()).isTrue();
        assertThat(service.sourceContext(accepted.jobId())).isEqualTo(context());
        assertThat(service.promptPreparation(accepted.jobId()).artifacts()).hasSize(9);
        assertThat(service.promptPreparation(accepted.jobId()).artifacts())
                .extracting(artifact -> artifact.displayName())
                .contains("ui-explorer/functional-writing-contract.md");
        verify(analysisProvider, times(1)).analyze(any(), any(), any(), any(), any(), any(), any());
        verify(localRunPersistence).persistTerminalSnapshot(
                org.mockito.ArgumentMatchers.argThat(snapshot ->
                        snapshot.status() == UiExplorerJobStatus.COMPLETED
                                && snapshot.result() != null
                                && snapshot.report() != null)
        );
    }

    @Test
    void shouldConvertCrmSourceSelectionFailureIntoBlockedAsyncJob() {
        var sourceContextService = mock(pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextService.class);
        when(sourceContextService.buildContext(
                eq("crm-agent-portal"), eq("main"), eq("crm-contact-preferences"),
                eq("crm-commit-abc123"), anyList()
        )).thenThrow(new UiExplorerFrontendNotEligibleException("crm-agent-portal"));
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        var executor = new CapturingTaskExecutor();
        var service = UiExplorerJobServiceTestCreator.create(sourceContextService, analysisProvider, executor);

        var accepted = service.startJob(request());
        executor.runCaptured();
        var blocked = service.getJob(accepted.jobId());

        assertThat(blocked.status()).isEqualTo(UiExplorerJobStatus.BLOCKED);
        assertThat(blocked.errorCode()).isEqualTo("UI_EXPLORER_FRONTEND_NOT_ELIGIBLE");
        assertThat(blocked.currentStepCode()).isNull();
        assertThat(blocked.steps()).extracting(step -> step.code() + ":" + step.status())
                .containsExactly(
                        "SCREEN_DISCOVERY:BLOCKED",
                        "SOURCE_CONTEXT:BLOCKED",
                        "AI_PREPARATION:SKIPPED",
                        "AI_ANALYSIS:SKIPPED"
                );
        assertThat(blocked.outputAvailability().status()).isEqualTo(UiExplorerOutputAvailabilityStatus.BLOCKED);
        assertThat(blocked.result()).isNull();
        verify(analysisProvider, times(0)).analyze(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPublishSafePartialCrmResultForMalformedAiResponse() {
        var sourceContextService = UiExplorerJobServiceTestCreator.sourceContextService(context());
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        var result = result(null);
        var report = UiExplorerJobServiceTestCreator.report("crm-malformed-report", result);
        when(analysisProvider.analyze(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UiExplorerAiAnalysis(
                        UiExplorerAiAnalysisStatus.MALFORMED,
                        result,
                        null,
                        "raw synthetic CRM prompt",
                        "crm-malformed-session",
                        report,
                        List.of("Synthetic CRM response was malformed.")
                ));
        var executor = new CapturingTaskExecutor();
        var service = UiExplorerJobServiceTestCreator.create(sourceContextService, analysisProvider, executor);

        var accepted = service.startJob(request());
        executor.runCaptured();
        var partial = service.getJob(accepted.jobId());

        assertThat(partial.status()).isEqualTo(UiExplorerJobStatus.PARTIAL);
        assertThat(partial.errorCode()).isEqualTo("UI_EXPLORER_AI_RESPONSE_MALFORMED");
        assertThat(partial.result()).isEqualTo(result);
        assertThat(partial.report()).isEqualTo(report);
        assertThat(partial.preparedPrompt()).contains("UI Explorer canonical prompt");
        assertThat(partial.outputAvailability().status()).isEqualTo(UiExplorerOutputAvailabilityStatus.AVAILABLE);
    }

    @Test
    void shouldPublishControlledBlockedStateWhenCrmAiReadinessFails() {
        var sourceContextService = UiExplorerJobServiceTestCreator.sourceContextService(context());
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        var fallback = result(null);
        when(analysisProvider.analyze(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UiExplorerAiAnalysis(
                        UiExplorerAiAnalysisStatus.BLOCKED,
                        fallback,
                        null,
                        null,
                        null,
                        UiExplorerJobServiceTestCreator.report("crm-blocked-report", fallback),
                        List.of("Synthetic CRM source coverage is insufficient for AI execution.")
                ));
        var executor = new CapturingTaskExecutor();
        var service = UiExplorerJobServiceTestCreator.create(sourceContextService, analysisProvider, executor);

        var accepted = service.startJob(request());
        executor.runCaptured();
        var blocked = service.getJob(accepted.jobId());

        assertThat(blocked.status()).isEqualTo(UiExplorerJobStatus.BLOCKED);
        assertThat(blocked.errorCode()).isEqualTo("UI_EXPLORER_AI_READINESS_BLOCKED");
        assertThat(blocked.errorMessage()).contains("Synthetic CRM source coverage");
        assertThat(blocked.result()).isNull();
        assertThat(blocked.report()).isNull();
        assertThat(blocked.steps()).filteredOn(step -> "AI_ANALYSIS".equals(step.code()))
                .singleElement().extracting(step -> step.status()).isEqualTo("BLOCKED");
        assertThat(blocked.preparedPrompt()).contains("UI Explorer canonical prompt");
    }

    @Test
    void shouldHideUnexpectedCopilotFailureAndMarkCrmJobFailed() {
        var sourceContextService = UiExplorerJobServiceTestCreator.sourceContextService(context());
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        when(analysisProvider.analyze(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("sensitive synthetic CRM provider detail"));
        var executor = new CapturingTaskExecutor();
        var service = UiExplorerJobServiceTestCreator.create(sourceContextService, analysisProvider, executor);

        var accepted = service.startJob(request());
        executor.runCaptured();
        var failed = service.getJob(accepted.jobId());

        assertThat(failed.status()).isEqualTo(UiExplorerJobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("UI_EXPLORER_ANALYSIS_FAILED");
        assertThat(failed.errorMessage()).doesNotContain("sensitive");
        assertThat(failed.result()).isNull();
        assertThat(failed.report()).isNull();
        assertThat(failed.preparedPrompt()).contains("UI Explorer canonical prompt");
    }

    @Test
    void shouldReturnControlledCrmFailureWhenAsyncJobCannotBeScheduled() {
        TaskExecutor rejectingExecutor = task -> {
            throw new IllegalStateException("synthetic CRM executor unavailable");
        };
        var service = UiExplorerJobServiceTestCreator.create(
                UiExplorerJobServiceTestCreator.sourceContextService(context()),
                mock(UiExplorerAnalysisProvider.class),
                rejectingExecutor
        );

        var failed = service.startJob(request());

        assertThat(failed.status()).isEqualTo(UiExplorerJobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("UI_EXPLORER_JOB_SCHEDULING_FAILED");
        assertThat(failed.errorMessage()).doesNotContain("executor");
        assertThat(failed.preparedPrompt()).isNull();
    }

    @Test
    void shouldKeepCompletedCrmJobWhenLocalHistoryPersistenceFails() {
        var analysisProvider = mock(UiExplorerAnalysisProvider.class);
        var result = result(usage());
        when(analysisProvider.analyze(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UiExplorerAiAnalysis(
                        UiExplorerAiAnalysisStatus.COMPLETED,
                        result,
                        usage(),
                        "raw synthetic CRM prompt",
                        "crm-persistence-failure-session",
                        UiExplorerJobServiceTestCreator.report("crm-persistence-failure-report", result),
                        List.of()
                ));
        var localRunPersistence = mock(UiExplorerLocalRunPersistence.class);
        doThrow(new IllegalStateException("synthetic CRM local history unavailable"))
                .when(localRunPersistence).persistTerminalSnapshot(any());
        var executor = new CapturingTaskExecutor();
        var service = UiExplorerJobServiceTestCreator.create(
                UiExplorerJobServiceTestCreator.sourceContextService(context()),
                analysisProvider,
                executor,
                localRunPersistence
        );

        var accepted = service.startJob(request());
        executor.runCaptured();

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(UiExplorerJobStatus.COMPLETED);
        verify(localRunPersistence).persistTerminalSnapshot(any());
    }

    @Test
    void shouldRejectUnknownCrmJobAndInternalArtifacts() {
        var service = UiExplorerJobServiceTestCreator.create(
                mock(pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextService.class),
                mock(UiExplorerAnalysisProvider.class),
                new CapturingTaskExecutor()
        );

        assertThatThrownBy(() -> service.getJob("crm-missing-job"))
                .isInstanceOf(UiExplorerJobNotFoundException.class)
                .hasMessageContaining("crm-missing-job");
        assertThatThrownBy(() -> service.sourceContext("crm-missing-job"))
                .isInstanceOf(UiExplorerJobNotFoundException.class);
        assertThatThrownBy(() -> service.promptPreparation("crm-missing-job"))
                .isInstanceOf(UiExplorerJobNotFoundException.class);
    }

    private static UiExplorerResultResponse result(AnalysisAiUsage usage) {
        return new UiExplorerResultResponse(
                context().screen(),
                request().scenarioDescription(),
                context().sourceRevision(),
                "The strongly anonymized CRM view maintains contact preferences.",
                List.of(),
                UiExplorerClaimConfidence.CONFIRMED,
                context().visibilityLimits(),
                List.of(),
                usage
        );
    }

    private static AnalysisAiUsage usage() {
        return new AnalysisAiUsage(
                100, 50, 10, 0, 160, 0.01, 200, 1,
                "gpt-5.4", 200_000L, 1_000L, 4L
        );
    }

    private static AnalysisAiActivityEvent activity() {
        return new AnalysisAiActivityEvent(
                "crm-ai-event-1", null, "tool", "source", "COMPLETED",
                "Read synthetic CRM validator", "Scoped CRM source evidence was read.",
                "crm-turn-1", "crm-interaction-1", "crm-tool-call-1", "gitlab_read_repository_file",
                Instant.parse("2026-08-15T10:00:00Z"), Map.of("scope", "synthetic-crm")
        );
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private Runnable captured;

        @Override
        public void execute(Runnable task) {
            captured = task;
        }

        private void runCaptured() {
            assertThat(captured).isNotNull();
            captured.run();
        }

        private void runCapturedTwice() {
            runCaptured();
            runCaptured();
        }
    }
}
