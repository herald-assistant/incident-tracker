package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiAssessmentService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiRunResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiRunner;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreement;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreementStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiAssessment;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportFactory;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContextStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationDeterministicBuildResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationJobNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace.RuntimeConfigurationVerificationLocalRunPersistence;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotation;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotationKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
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

class RuntimeConfigurationVerificationJobServiceTest {

    private final RuntimeConfigurationScopeResolver scopeResolver =
            mock(RuntimeConfigurationScopeResolver.class);
    private final RuntimeConfigurationDeterministicContextService deterministicService =
            mock(RuntimeConfigurationDeterministicContextService.class);
    private final RuntimeConfigurationDeepContextService deepService =
            mock(RuntimeConfigurationDeepContextService.class);
    private final RuntimeConfigurationPromptPreparationService promptService =
            mock(RuntimeConfigurationPromptPreparationService.class);
    private final RuntimeConfigurationAiRunner aiRunner = mock(RuntimeConfigurationAiRunner.class);
    private final RuntimeConfigurationAiAssessmentService assessmentService =
            mock(RuntimeConfigurationAiAssessmentService.class);
    private final RuntimeConfigurationReportFactory reportFactory =
            mock(RuntimeConfigurationReportFactory.class);
    private final RuntimeConfigurationDiffAnnotationService diffAnnotationService =
            mock(RuntimeConfigurationDiffAnnotationService.class);
    private final RuntimeConfigurationVerificationLocalRunPersistence persistence =
            mock(RuntimeConfigurationVerificationLocalRunPersistence.class);
    private final AnalysisAiAuthRefResolver authRefResolver = mock(AnalysisAiAuthRefResolver.class);
    private final CopilotAccessTokenResolver accessTokenResolver = mock(CopilotAccessTokenResolver.class);
    private final CapturingTaskExecutor executor = new CapturingTaskExecutor();
    private final RuntimeConfigurationComponentRunner componentRunner =
            new RuntimeConfigurationComponentRunner(
                    scopeResolver,
                    deterministicService,
                    deepService,
                    promptService,
                    aiRunner,
                    assessmentService,
                    reportFactory,
                    diffAnnotationService
            );
    private final RuntimeConfigurationVerificationJobService service =
            new RuntimeConfigurationVerificationJobService(
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
                            RuntimeConfigurationDeterministicContextListener.class
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
                .thenReturn(new RuntimeConfigurationPromptPreparation(
                        "safe prompt",
                        Map.of("manifest.json", "{}"),
                        List.of()
                ));
        when(aiRunner.run(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RuntimeConfigurationAiRunResult(completedAssessment(), null));
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
        assertEquals(RuntimeConfigurationVerificationStatus.NO_BLOCKING_ANOMALIES,
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
        var incomplete = new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend",
                "dev1",
                "zt001",
                RuntimeConfigurationDeterministicStatus.INCOMPLETE,
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
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, component.result().status());
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
        when(scopeResolver.resolve("runtime-config", "billing-backend")).thenReturn(new RuntimeConfigurationScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "billing-backend",
                "Billing Backend",
                "billing"
        ));
        var batchRequest = new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
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
                RuntimeConfigurationVerificationJobNotFoundException.class,
                () -> service.getJob("missing-job")
        );
    }

    @Test
    void shouldKeepInvalidScopeFailureInsideComponentRun() {
        when(scopeResolver.resolve("runtime-config", "crm-backend")).thenThrow(
                RuntimeConfigurationScopeException.configurationDirectoryMissing("crm-backend")
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
                RuntimeConfigurationScopeException.configurationDirectoryMissing("billing-backend")
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
                RuntimeConfigurationScopeException.configurationDirectoryMissing("crm-backend")
        );
        when(scopeResolver.resolve("runtime-config", "billing-backend")).thenThrow(
                RuntimeConfigurationScopeException.configurationDirectoryMissing("billing-backend")
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
        var deep = deepContext(RuntimeConfigurationDeepContextStatus.COMPLETE, List.of());
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
                RuntimeConfigurationDeepContextStatus.PARTIAL,
                List.of("Code ref was not confirmed.")
        );
        stubDeep(deep);

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());
        var component = completed.components().get(0);

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, component.result().status());
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
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertFalse(completed.toString().contains("do-not-expose-deep-detail"));
    }

    @Test
    void shouldKeepDeterministicResultWhenAiFails() {
        stubDeep(deepContext(RuntimeConfigurationDeepContextStatus.COMPLETE, List.of()));
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
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, component.result().status());
        assertNotNull(component.result().deterministicResult());
        assertFalse(completed.toString().contains("do-not-expose-ai-detail"));
    }

    @Test
    void shouldExposeOnlyGenericErrorWhenConfigurationSourceFails() {
        doAnswer(invocation -> {
                    invocation.getArgument(3, RuntimeConfigurationDeterministicContextListener.class)
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

    private static RuntimeConfigurationScope scope() {
        return new RuntimeConfigurationScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend"
        );
    }

    static RuntimeConfigurationDeterministicContext deterministic() {
        return new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend",
                "dev1",
                "zt001",
                RuntimeConfigurationDeterministicStatus.NO_BLOCKING_ANOMALIES,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    static RuntimeConfigurationDiffProjection configurationDiff() {
        return new RuntimeConfigurationDiffProjection("dev1", "zt001", List.of());
    }

    static RuntimeConfigurationDeterministicBuildResult deterministicBuild() {
        return new RuntimeConfigurationDeterministicBuildResult(
                deterministic(),
                configurationDiff()
        );
    }

    static RuntimeConfigurationAiAssessment completedAssessment() {
        return new RuntimeConfigurationAiAssessment(
                new RuntimeConfigurationAiSecondOpinion(
                        RuntimeConfigurationAiExecutionStatus.COMPLETED,
                        RuntimeConfigurationAiConclusion.NO_CONCERN,
                        RuntimeConfigurationAiConfidence.HIGH,
                        "No concern.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new RuntimeConfigurationAgreement(
                        RuntimeConfigurationAgreementStatus.AGREEMENT,
                        "Results agree.",
                        List.of(),
                        List.of()
                ),
                RuntimeConfigurationVerificationStatus.NO_BLOCKING_ANOMALIES,
                null
        );
    }

    static RuntimeConfigurationAiAssessment incompleteAssessment() {
        return new RuntimeConfigurationAiAssessment(
                RuntimeConfigurationAiSecondOpinion.incomplete("AI did not complete."),
                new RuntimeConfigurationAgreement(
                        RuntimeConfigurationAgreementStatus.NOT_ASSESSED,
                        "Not assessed.",
                        List.of(),
                        List.of()
                ),
                RuntimeConfigurationVerificationStatus.INCOMPLETE,
                null
        );
    }

    private static RuntimeConfigurationDiffAnnotation annotation() {
        return new RuntimeConfigurationDiffAnnotation(
                "observation-1",
                RuntimeConfigurationDiffAnnotationKind.OBSERVATION,
                "Runtime behavior may change.",
                null,
                false,
                List.of("difference-1"),
                List.of()
        );
    }

    static RuntimeConfigurationVerificationJobStartRequest request() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                List.of("crm-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    static RuntimeConfigurationVerificationJobStartRequest deepRequest() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                List.of("crm-backend"),
                "dev1",
                "zt001",
                "release-42",
                null,
                null
        );
    }

    static RuntimeConfigurationVerificationJobStartRequest batchRequest() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                List.of("crm-backend", "billing-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    private RuntimeConfigurationDeepContext deepContext(
            RuntimeConfigurationDeepContextStatus status,
            List<String> visibilityLimits
    ) {
        var context = mock(RuntimeConfigurationDeepContext.class);
        when(context.status()).thenReturn(status);
        when(context.visibilityLimits()).thenReturn(visibilityLimits);
        return context;
    }

    private void stubDeep(RuntimeConfigurationDeepContext deep) {
        when(deepService.build(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var listener = invocation.getArgument(5, RuntimeConfigurationDeepContextListener.class);
                    listener.onOperationalContextStarted();
                    listener.onOperationalContextCompleted();
                    listener.onCodeGroundingStarted();
                    listener.onCodeGroundingCompleted();
                    listener.onOwnershipStarted();
                    listener.onOwnershipCompleted(deep);
                    return Optional.of(deep);
                });
    }

    private void stubDeterministic(RuntimeConfigurationDeterministicContext context) {
        doAnswer(invocation -> {
                    var listener = invocation.getArgument(
                            3,
                            RuntimeConfigurationDeterministicContextListener.class
                    );
                    var build = new RuntimeConfigurationDeterministicBuildResult(
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
