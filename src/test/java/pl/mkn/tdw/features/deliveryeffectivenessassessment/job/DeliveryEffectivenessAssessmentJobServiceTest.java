package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScoringService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAiResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAiAnalysis;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentStartException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceDiscoveryService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceResult;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;

import java.time.LocalDate;
import java.util.List;
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
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.source;

class DeliveryEffectivenessAssessmentJobServiceTest {

    private final DeliveryAssessmentSourceDiscoveryService source = mock(DeliveryAssessmentSourceDiscoveryService.class);
    private final DeliveryUnitAssessmentProvider assessmentProvider = mock(DeliveryUnitAssessmentProvider.class);
    private final DeliveryAssessmentLocalRunPersistence persistence = mock(DeliveryAssessmentLocalRunPersistence.class);
    private final DeliveryAssessmentUnitExecutor unitExecutor = mock(DeliveryAssessmentUnitExecutor.class);
    private final TaskExecutor taskExecutor = mock(TaskExecutor.class);
    private final AnalysisAiAuthRefResolver authRefResolver = mock(AnalysisAiAuthRefResolver.class);
    private final CopilotAccessTokenResolver accessTokenResolver = mock(CopilotAccessTokenResolver.class);
    private DeliveryEffectivenessAssessmentJobService service;

    @BeforeEach
    void setUp() {
        var workspace = new LocalWorkspaceProperties();
        var properties = new DeliveryEffectivenessAssessmentProperties();
        when(authRefResolver.resolveForCurrentRequest()).thenReturn(AnalysisAiAuthRef.localToken("test"));
        service = new DeliveryEffectivenessAssessmentJobService(
                source,
                new DeliveryUnitBuilder(),
                new DeliveryEvidencePacketBuilder(properties),
                assessmentProvider,
                new DeliveryAssessmentScoringService(),
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
                .isInstanceOf(DeliveryAssessmentStartException.class)
                .hasMessageContaining("initial Analysis History snapshot");
        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void shouldPreserveUsageAndReportWhenAiReturnsInsufficientEvidence() {
        var usage = new AnalysisAiUsage(100, 20, 30, 0, 120, 1.0, 500, 4, "gpt-5", null, null, null);
        var report = new AnalysisReport(
                "report-1", "Assessment", "DU-CRM-1", "Insufficient evidence", List.of(), AnalysisReportMeta.empty()
        );
        when(source.discover(any(), any())).thenReturn(new DeliveryAssessmentSourceResult(
                "effective-jql",
                1,
                false,
                List.of(source("CRM-1", mergeRequest(1, "src/A.java", "+A"))),
                List.of()
        ));
        when(assessmentProvider.analyze(anyString(), any(), any(), any(), any())).thenReturn(new DeliveryUnitAiAnalysis(
                new DeliveryAiResponse(
                        "INSUFFICIENT_EVIDENCE", null, 0.2, List.of(), List.of(), List.of("Diff was incomplete.")
                ),
                usage,
                "prompt",
                "session-1",
                report
        ));
        DeliveryAssessmentUnitExecutor directUnitExecutor = task -> {
            task.run();
            return CompletableFuture.completedFuture(null);
        };
        TaskExecutor directTaskExecutor = Runnable::run;
        var directService = new DeliveryEffectivenessAssessmentJobService(
                source,
                new DeliveryUnitBuilder(),
                new DeliveryEvidencePacketBuilder(new DeliveryEffectivenessAssessmentProperties()),
                assessmentProvider,
                new DeliveryAssessmentScoringService(),
                persistence,
                directUnitExecutor,
                directTaskExecutor,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                new LocalWorkspaceProperties(),
                new DeliveryEffectivenessAssessmentProperties()
        );

        var snapshot = directService.startJob(request());

        assertThat(snapshot.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("NOT_SCORABLE");
            assertThat(unit.usage()).isEqualTo(usage);
            assertThat(unit.report()).isEqualTo(report);
            assertThat(unit.visibilityLimits()).contains("Diff was incomplete.");
        });
        assertThat(snapshot.aggregate().usage()).isEqualTo(usage);
        assertThat(snapshot.visibilityLimits()).contains("Diff was incomplete.");
    }

    private DeliveryEffectivenessAssessmentJobStartRequest request() {
        return new DeliveryEffectivenessAssessmentJobStartRequest(
                "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                "gpt-5", "medium"
        );
    }
}
