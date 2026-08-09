package pl.mkn.tdw.features.configdriftviewer.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiAssessmentService;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiRunResult;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiRunner;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreement;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreementStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiAssessment;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparationService;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportFactory;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextService;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextListener;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContextStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerDeterministicBuildResult;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerDeterministicContextListener;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerDeterministicContextService;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.error.ConfigDriftViewerJobNotFoundException;
import pl.mkn.tdw.features.configdriftviewer.job.localworkspace.ConfigDriftViewerLocalRunPersistence;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotation;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotationKind;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotationService;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeException;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class ConfigDriftViewerJobServiceTest {

    private final ConfigDriftViewerScopeResolver scopeResolver =
            mock(ConfigDriftViewerScopeResolver.class);
    private final ConfigDriftViewerDeterministicContextService deterministicService =
            mock(ConfigDriftViewerDeterministicContextService.class);
    private final ConfigDriftViewerDeepContextService deepService =
            mock(ConfigDriftViewerDeepContextService.class);
    private final ConfigDriftViewerPromptPreparationService promptService =
            mock(ConfigDriftViewerPromptPreparationService.class);
    private final ConfigDriftViewerAiRunner aiRunner = mock(ConfigDriftViewerAiRunner.class);
    private final ConfigDriftViewerAiAssessmentService assessmentService =
            mock(ConfigDriftViewerAiAssessmentService.class);
    private final ConfigDriftViewerReportFactory reportFactory =
            mock(ConfigDriftViewerReportFactory.class);
    private final ConfigDriftViewerDiffAnnotationService diffAnnotationService =
            mock(ConfigDriftViewerDiffAnnotationService.class);
    private final ConfigDriftViewerLocalRunPersistence persistence =
            mock(ConfigDriftViewerLocalRunPersistence.class);
    private final AnalysisAiAuthRefResolver authRefResolver = mock(AnalysisAiAuthRefResolver.class);
    private final CopilotAccessTokenResolver accessTokenResolver = mock(CopilotAccessTokenResolver.class);
    private final CapturingTaskExecutor executor = new CapturingTaskExecutor();
    private final ConfigDriftViewerComponentRunner componentRunner =
            new ConfigDriftViewerComponentRunner(
                    scopeResolver,
                    deterministicService,
                    deepService,
                    promptService,
                    aiRunner,
                    assessmentService,
                    reportFactory,
                    diffAnnotationService
            );
    private final ConfigDriftViewerJobService service =
            new ConfigDriftViewerJobService(
                    componentRunner,
                    persistence,
                    executor,
                    task -> {
                        task.run();
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    },
                    authRefResolver,
                    new CopilotRunAuthMapper(),
                    accessTokenResolver
            );

    @BeforeEach
    void setUp() {
        when(scopeResolver.resolve("runtime-config", "crm-backend")).thenReturn(scope());
        when(authRefResolver.resolveForCurrentRequest()).thenReturn(AnalysisAiAuthRef.localToken(null));
        when(deterministicService.build(any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    var listener = invocation.getArgument(
                            3,
                            ConfigDriftViewerDeterministicContextListener.class
                    );
                    var build = deterministicBuild();
                    listener.onSourceStarted();
                    listener.onSourceCompleted();
                    listener.onParseStarted();
                    listener.onParseCompleted();
                    listener.onDiffStarted();
                    listener.onDiffCompleted(build);
                    return build;
                });
        when(promptService.prepare(any(), any(), any()))
                .thenReturn(new ConfigDriftViewerPromptPreparation(
                        "safe prompt",
                        Map.of("manifest.json", "{}"),
                        List.of()
                ));
        when(aiRunner.run(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ConfigDriftViewerAiRunResult(completedAssessment(), null));
        when(diffAnnotationService.create(any(), any())).thenReturn(List.of(annotation()));
    }

    @Test
    void shouldReturnImmediateQueuedSnapshotAndCompleteBasicLifecycle() {
        var created = service.startJob(request());

        assertNotNull(created.jobId());
        assertEquals("QUEUED", created.status());
        assertEquals(created.createdAt(), created.updatedAt());
        assertNull(created.completedAt());
        assertEquals(1, executor.size());

        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED", completed.status());
        assertEquals(
                List.of("SOURCE", "PARSE", "DIFF"),
                component.steps().stream().map(step -> step.code()).toList()
        );
        assertEquals(ConfigDriftViewerStatus.NO_BLOCKING_ANOMALIES,
                component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertEquals(configurationDiff(), component.result().configurationDiff());
        assertTrue(component.result().configurationDiffAnnotations().isEmpty());
        assertNull(component.result().aiSecondOpinion());
        assertNull(component.result().agreement());
        assertNull(component.result().deepAnalysis());
        assertTrue(component.result().visibilityLimits().isEmpty());
        assertNull(component.result().prompt());
        assertNull(component.result().usage());
        assertNull(component.preparedPrompt());
        assertNull(component.report());
        assertTrue(component.aiActivityEvents().isEmpty());
        assertTrue(component.toolEvidenceSections().isEmpty());
        assertFalse(completed.imported());
        verifyNoInteractions(
                authRefResolver,
                accessTokenResolver,
                deepService,
                promptService,
                aiRunner,
                assessmentService,
                reportFactory,
                diffAnnotationService
        );
        verify(persistence, org.mockito.Mockito.atLeast(6)).persistRunSnapshot(any());
    }

    @Test
    void shouldMapIncompleteDeterministicBasicResultWithoutInvokingAi() {
        var incomplete = new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.INCOMPLETE,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        stubDeterministic(incomplete);

        var created = service.startJob(request());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(ConfigDriftViewerStatus.INCOMPLETE, component.result().status());
        assertTrue(component.result().visibilityLimits().isEmpty());
        assertNull(completed.errorCode());
        verifyNoInteractions(
                authRefResolver,
                accessTokenResolver,
                deepService,
                promptService,
                aiRunner,
                assessmentService,
                reportFactory,
                diffAnnotationService
        );
    }

    @Test
    void shouldCreateIndependentJobs() {
        var first = service.startJob(request());
        var second = service.startJob(request());

        assertNotEquals(first.jobId(), second.jobId());
    }

    @Test
    void shouldPreserveRequestOrderInComponentSnapshots() {
        when(scopeResolver.resolve("runtime-config", "billing-backend")).thenReturn(new ConfigDriftViewerScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "billing-backend",
                "Billing Backend",
                "billing"
        ));
        var batchRequest = new ConfigDriftViewerJobStartRequest(
                ConfigDriftViewerMode.BASIC,
                "runtime-config",
                List.of("crm-backend", "billing-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );

        var created = service.startJob(batchRequest);
        assertEquals(List.of("crm-backend", "billing-backend"), created.systemIds());
        assertEquals(2, created.components().size());
        assertEquals(1, executor.size());

        executor.runNext();
        var completed = service.getJob(created.jobId());
        assertEquals("COMPLETED", completed.status());
        assertEquals(
                List.of("crm-backend", "billing-backend"),
                completed.components().stream().map(component -> component.systemId()).toList()
        );
        assertTrue(completed.components().stream().allMatch(component -> "COMPLETED".equals(component.status())));
        assertNotEquals(
                completed.components().get(0).componentRunId(),
                completed.components().get(1).componentRunId()
        );
    }

    @Test
    void shouldRejectUnknownJob() {
        assertThrows(
                ConfigDriftViewerJobNotFoundException.class,
                () -> service.getJob("missing-job")
        );
    }

    @Test
    void shouldKeepInvalidScopeFailureInsideComponentRun() {
        when(scopeResolver.resolve("runtime-config", "crm-backend")).thenThrow(
                ConfigDriftViewerScopeException.configurationDirectoryMissing("crm-backend")
        );

        var created = service.startJob(request());
        assertEquals("QUEUED", created.status());
        assertEquals(1, executor.size());

        executor.runNext();
        var failed = service.getJob(created.jobId());
        assertEquals("FAILED", failed.status());
        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_FAILED", failed.components().get(0).errorCode());
    }

    @Test
    void shouldCompleteWithLimitationsWhenOneComponentFails() {
        when(scopeResolver.resolve("runtime-config", "billing-backend")).thenThrow(
                ConfigDriftViewerScopeException.configurationDirectoryMissing("billing-backend")
        );

        var created = service.startJob(batchRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertNotNull(completed.components().get(0).result());
        assertEquals("COMPLETED", completed.components().get(0).status());
        assertNull(completed.components().get(1).result());
        assertEquals("FAILED", completed.components().get(1).status());
    }

    @Test
    void shouldFailBatchWhenNoComponentProducesResult() {
        when(scopeResolver.resolve("runtime-config", "crm-backend")).thenThrow(
                ConfigDriftViewerScopeException.configurationDirectoryMissing("crm-backend")
        );
        when(scopeResolver.resolve("runtime-config", "billing-backend")).thenThrow(
                ConfigDriftViewerScopeException.configurationDirectoryMissing("billing-backend")
        );

        var created = service.startJob(batchRequest());
        executor.runNext();
        var failed = service.getJob(created.jobId());

        assertEquals("FAILED", failed.status());
        assertTrue(failed.components().stream().allMatch(component -> "FAILED".equals(component.status())));
        assertTrue(failed.components().stream().allMatch(component -> component.result() == null));
    }

    @Test
    void shouldCompleteDeepLifecycle() {
        var deep = deepContext(ConfigDriftViewerDeepContextStatus.COMPLETE, List.of());
        stubDeep(deep);

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED", completed.status());
        assertEquals(
                List.of(
                        "SOURCE",
                        "PARSE",
                        "DIFF",
                        "OPERATIONAL_CONTEXT",
                        "CODE_GROUNDING",
                        "OWNERSHIP",
                        "AI"
                ),
                component.steps().stream().map(step -> step.code()).toList()
        );
        assertEquals(deep, component.result().deepAnalysis());
        assertEquals(List.of(annotation()), component.result().configurationDiffAnnotations());
        verify(authRefResolver).resolveForCurrentRequest();
        verify(accessTokenResolver).resolve(any());
        verify(promptService).prepare(any(), any(), any());
        verify(aiRunner).run(anyString(), any(), any(), any(), any(), any(), any(), any());
        verify(diffAnnotationService).create(any(), any());
    }

    @Test
    void shouldKeepDeterministicResultWhenDeepIsPartial() {
        var deep = deepContext(
                ConfigDriftViewerDeepContextStatus.PARTIAL,
                List.of("Code ref was not confirmed.")
        );
        stubDeep(deep);

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(ConfigDriftViewerStatus.INCOMPLETE, component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertTrue(component.result().visibilityLimits().contains("Code ref was not confirmed."));
    }

    @Test
    void shouldKeepDeterministicResultWhenDeepFails() {
        when(deepService.build(
                any(), anyString(), anyString(), any(), any(), any()
        )).thenThrow(new IllegalStateException("do-not-expose-deep-detail"));

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(ConfigDriftViewerStatus.INCOMPLETE, component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertFalse(completed.toString().contains("do-not-expose-deep-detail"));
    }

    @Test
    void shouldKeepDeterministicResultWhenAiFails() {
        stubDeep(deepContext(ConfigDriftViewerDeepContextStatus.COMPLETE, List.of()));
        when(aiRunner.run(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("do-not-expose-ai-detail"));
        when(assessmentService.assess(any(), any(), any(), any(), any()))
                .thenReturn(incompleteAssessment());

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals("RUNTIME_CONFIGURATION_AI_INCOMPLETE", component.errorCode());
        assertEquals(ConfigDriftViewerStatus.INCOMPLETE, component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertFalse(completed.toString().contains("do-not-expose-ai-detail"));
    }

    @Test
    void shouldExposeOnlyGenericErrorWhenConfigurationSourceFails() {
        doAnswer(invocation -> {
                    invocation.getArgument(3, ConfigDriftViewerDeterministicContextListener.class)
                            .onSourceStarted();
                    throw new IllegalStateException("secret=value-from-file");
                })
                .when(deterministicService)
                .build(any(), anyString(), anyString(), any());

        var created = service.startJob(request());
        executor.runNext();
        var failed = service.getJob(created.jobId());

        assertEquals("FAILED", failed.status());
        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_FAILED", failed.errorCode());
        assertFalse(failed.errorMessage().contains("secret"));
        assertFalse(failed.toString().contains("value-from-file"));
    }

    private static ConfigDriftViewerScope scope() {
        return new ConfigDriftViewerScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend"
        );
    }

    static ConfigDriftViewerDeterministicContext deterministic() {
        return new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.NO_BLOCKING_ANOMALIES,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    static ConfigDriftViewerDiffProjection configurationDiff() {
        return new ConfigDriftViewerDiffProjection("dev1", "zt001", List.of());
    }

    static ConfigDriftViewerDeterministicBuildResult deterministicBuild() {
        return new ConfigDriftViewerDeterministicBuildResult(
                deterministic(),
                configurationDiff()
        );
    }

    static ConfigDriftViewerAiAssessment completedAssessment() {
        return new ConfigDriftViewerAiAssessment(
                new ConfigDriftViewerAiSecondOpinion(
                        ConfigDriftViewerAiExecutionStatus.COMPLETED,
                        ConfigDriftViewerAiConclusion.NO_CONCERN,
                        ConfigDriftViewerAiConfidence.HIGH,
                        "No concern.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ConfigDriftViewerAgreement(
                        ConfigDriftViewerAgreementStatus.AGREEMENT,
                        "Results agree.",
                        List.of(),
                        List.of()
                ),
                ConfigDriftViewerStatus.NO_BLOCKING_ANOMALIES,
                null
        );
    }

    static ConfigDriftViewerAiAssessment incompleteAssessment() {
        return new ConfigDriftViewerAiAssessment(
                ConfigDriftViewerAiSecondOpinion.incomplete("AI did not complete."),
                new ConfigDriftViewerAgreement(
                        ConfigDriftViewerAgreementStatus.NOT_ASSESSED,
                        "Not assessed.",
                        List.of(),
                        List.of()
                ),
                ConfigDriftViewerStatus.INCOMPLETE,
                null
        );
    }

    private static ConfigDriftViewerDiffAnnotation annotation() {
        return new ConfigDriftViewerDiffAnnotation(
                "observation-1",
                ConfigDriftViewerDiffAnnotationKind.OBSERVATION,
                "Runtime behavior may change.",
                null,
                false,
                List.of("difference-1"),
                List.of()
        );
    }

    static ConfigDriftViewerJobStartRequest request() {
        return new ConfigDriftViewerJobStartRequest(
                ConfigDriftViewerMode.BASIC,
                "runtime-config",
                List.of("crm-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    static ConfigDriftViewerJobStartRequest deepRequest() {
        return new ConfigDriftViewerJobStartRequest(
                ConfigDriftViewerMode.DEEP,
                "runtime-config",
                List.of("crm-backend"),
                "dev1",
                "zt001",
                "release-42",
                null,
                null
        );
    }

    static ConfigDriftViewerJobStartRequest batchRequest() {
        return new ConfigDriftViewerJobStartRequest(
                ConfigDriftViewerMode.BASIC,
                "runtime-config",
                List.of("crm-backend", "billing-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    private ConfigDriftViewerDeepContext deepContext(
            ConfigDriftViewerDeepContextStatus status,
            List<String> visibilityLimits
    ) {
        var context = mock(ConfigDriftViewerDeepContext.class);
        when(context.status()).thenReturn(status);
        when(context.visibilityLimits()).thenReturn(visibilityLimits);
        return context;
    }

    private void stubDeep(ConfigDriftViewerDeepContext deep) {
        when(deepService.build(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var listener = invocation.getArgument(5, ConfigDriftViewerDeepContextListener.class);
                    listener.onOperationalContextStarted();
                    listener.onOperationalContextCompleted();
                    listener.onCodeGroundingStarted();
                    listener.onCodeGroundingCompleted();
                    listener.onOwnershipStarted();
                    listener.onOwnershipCompleted(deep);
                    return Optional.of(deep);
                });
    }

    private void stubDeterministic(ConfigDriftViewerDeterministicContext context) {
        doAnswer(invocation -> {
                    var listener = invocation.getArgument(
                            3,
                            ConfigDriftViewerDeterministicContextListener.class
                    );
                    var build = new ConfigDriftViewerDeterministicBuildResult(
                            context,
                            configurationDiff()
                    );
                    listener.onSourceStarted();
                    listener.onSourceCompleted();
                    listener.onParseStarted();
                    listener.onParseCompleted();
                    listener.onDiffStarted();
                    listener.onDiffCompleted(build);
                    return build;
                })
                .when(deterministicService)
                .build(any(), anyString(), anyString(), any());
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private int size() {
            return tasks.size();
        }

        private void runNext() {
            tasks.remove().run();
        }
    }
}
