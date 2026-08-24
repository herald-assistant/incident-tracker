package pl.mkn.tdw.features.deliveryscopecomplexity.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeComplexityProperties;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryScopeScoringService;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryAiResponse;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryPromptPreparationService;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryRawAiResponseListener;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryUnitAiAnalysis;
import pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStartRequest;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.error.DeliveryScopeStartException;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.localworkspace.DeliveryScopeLocalRunPersistence;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeSourceDiscoveryService;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeSourceResult;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.source;

class DeliveryScopeComplexityJobServiceTest {

    private final DeliveryScopeSourceDiscoveryService source = mock(DeliveryScopeSourceDiscoveryService.class);
    private final DeliveryUnitAssessmentProvider assessmentProvider = mock(DeliveryUnitAssessmentProvider.class);
    private final DeliveryPromptPreparationService promptPreparationService = mock(DeliveryPromptPreparationService.class);
    private final DeliveryScopeLocalRunPersistence persistence = mock(DeliveryScopeLocalRunPersistence.class);
    private final DeliveryScopeUnitExecutor unitExecutor = mock(DeliveryScopeUnitExecutor.class);
    private final TaskExecutor taskExecutor = mock(TaskExecutor.class);
    private final AnalysisAiAuthRefResolver authRefResolver = mock(AnalysisAiAuthRefResolver.class);
    private final CopilotAccessTokenResolver accessTokenResolver = mock(CopilotAccessTokenResolver.class);
    private DeliveryScopeComplexityJobService service;

    @BeforeEach
    void setUp() {
        var workspace = new LocalWorkspaceProperties();
        var properties = new DeliveryScopeComplexityProperties();
        when(authRefResolver.resolveForCurrentRequest()).thenReturn(AnalysisAiAuthRef.localToken("test"));
        when(promptPreparationService.prepare(any())).thenReturn(new DeliveryPromptPreparation(
                "one-shot prompt with skill and evidence",
                Map.of("delivery-scope-complexity/issues.md", "issue")
        ));
        service = new DeliveryScopeComplexityJobService(
                source,
                new DeliveryUnitBuilder(),
                new DeliveryEvidencePacketBuilder(),
                promptPreparationService,
                assessmentProvider,
                new DeliveryScopeScoringService(),
                persistence,
                unitExecutor,
                taskExecutor,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                workspace,
                properties
        );
    }

    @Test
    void shouldPersistQueuedSnapshotBeforeSchedulingBackgroundWork() {
        var snapshot = service.startJob(request());

        assertThat(snapshot.status()).isEqualTo("QUEUED");
        assertThat(service.getJob(snapshot.jobId()).status()).isEqualTo("QUEUED");
        var order = inOrder(persistence, taskExecutor);
        order.verify(persistence).persistRunSnapshot(any());
        order.verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void shouldRejectStartWhenInitialHistorySnapshotCannotBeStored() {
        doThrow(new IllegalStateException("disk unavailable"))
                .when(persistence).persistRunSnapshot(any());

        assertThatThrownBy(() -> service.startJob(request()))
                .isInstanceOf(DeliveryScopeStartException.class)
                .hasMessageContaining("initial Analysis History snapshot");
        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void shouldPreserveUsageWhenAiReturnsInsufficientEvidence() {
        var usage = new AnalysisAiUsage(100, 20, 30, 0, 120, 1.0, 500, 4, "gpt-5", null, null, null);
        when(source.discover(any(), any())).thenReturn(new DeliveryScopeSourceResult(
                "effective-jql",
                1,
                false,
                List.of(source("CRM-1", mergeRequest(1, "src/A.java", "+A"))),
                List.of()
        ));
        when(assessmentProvider.analyze(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(new DeliveryUnitAiAnalysis(
                new DeliveryAiResponse(
                        "INSUFFICIENT_EVIDENCE", null, 0.2, List.of(), List.of(), List.of("Diff was incomplete.")
                ),
                usage,
                "prompt",
                "session-1"
        ));
        DeliveryScopeUnitExecutor directUnitExecutor = task -> {
            task.run();
            return CompletableFuture.completedFuture(null);
        };
        TaskExecutor directTaskExecutor = Runnable::run;
        var directService = new DeliveryScopeComplexityJobService(
                source,
                new DeliveryUnitBuilder(),
                new DeliveryEvidencePacketBuilder(),
                promptPreparationService,
                assessmentProvider,
                new DeliveryScopeScoringService(),
                persistence,
                directUnitExecutor,
                directTaskExecutor,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                new LocalWorkspaceProperties(),
                new DeliveryScopeComplexityProperties()
        );

        var snapshot = directService.startJob(request());

        assertThat(snapshot.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("NOT_SCORABLE");
            assertThat(unit.usage()).isEqualTo(usage);
            assertThat(unit.visibilityLimits()).contains("Diff was incomplete.");
            assertThat(unit.preparedPrompt()).isEqualTo("one-shot prompt with skill and evidence");
            assertThat(unit.promptPreparedAt()).isNotNull();
        });
        assertThat(snapshot.steps()).anySatisfy(step -> {
            assertThat(step.code()).isEqualTo("AI_INPUT_PREPARATION");
            assertThat(step.itemCount()).isEqualTo(1);
        });
        assertThat(snapshot.aggregate().usage()).isEqualTo(usage);
    }

    @Test
    void shouldPersistRawResponseWhenAiResultCannotBeParsed() {
        var rawResponse = "The result is not valid JSON.";
        when(source.discover(any(), any())).thenReturn(new DeliveryScopeSourceResult(
                "effective-jql",
                1,
                false,
                List.of(source("CRM-1", mergeRequest(1, "src/A.java", "+A"))),
                List.of()
        ));
        when(assessmentProvider.analyze(anyString(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    DeliveryRawAiResponseListener listener = invocation.getArgument(6);
                    listener.onRawAiResponse(rawResponse);
                    throw new IllegalArgumentException("AI response did not contain JSON assessment.");
                });
        DeliveryScopeUnitExecutor directUnitExecutor = task -> {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        };
        var directService = new DeliveryScopeComplexityJobService(
                source,
                new DeliveryUnitBuilder(),
                new DeliveryEvidencePacketBuilder(),
                promptPreparationService,
                assessmentProvider,
                new DeliveryScopeScoringService(),
                persistence,
                directUnitExecutor,
                Runnable::run,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                new LocalWorkspaceProperties(),
                new DeliveryScopeComplexityProperties()
        );

        var snapshot = directService.startJob(request());

        assertThat(snapshot.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("FAILED");
            assertThat(unit.errorMessage()).isEqualTo("AI response did not contain JSON assessment.");
            assertThat(unit.rawAiResponse()).isEqualTo(rawResponse);
        });
    }

    private DeliveryScopeComplexityJobStartRequest request() {
        return new DeliveryScopeComplexityJobStartRequest(
                "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                "gpt-5", "medium"
        );
    }
}
