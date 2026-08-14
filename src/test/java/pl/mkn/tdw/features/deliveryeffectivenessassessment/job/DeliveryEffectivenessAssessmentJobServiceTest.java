package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScoringService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentStartException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceDiscoveryService;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private DeliveryEffectivenessAssessmentJobStartRequest request() {
        return new DeliveryEffectivenessAssessmentJobStartRequest(
                "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                "gpt-5", "medium"
        );
    }
}
