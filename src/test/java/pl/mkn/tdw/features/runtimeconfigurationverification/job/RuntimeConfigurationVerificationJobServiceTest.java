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
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationJobNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace.RuntimeConfigurationVerificationLocalRunPersistence;
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
    private final RuntimeConfigurationVerificationLocalRunPersistence persistence =
            mock(RuntimeConfigurationVerificationLocalRunPersistence.class);
    private final AnalysisAiAuthRefResolver authRefResolver = mock(AnalysisAiAuthRefResolver.class);
    private final CopilotAccessTokenResolver accessTokenResolver = mock(CopilotAccessTokenResolver.class);
    private final CapturingTaskExecutor executor = new CapturingTaskExecutor();
    private final RuntimeConfigurationVerificationJobService service =
            new RuntimeConfigurationVerificationJobService(
                    scopeResolver,
                    deterministicService,
                    deepService,
                    promptService,
                    aiRunner,
                    assessmentService,
                    reportFactory,
                    persistence,
                    executor,
                    authRefResolver,
                    new CopilotRunAuthMapper(),
                    accessTokenResolver
            );

    @BeforeEach
    void setUp() {
        when(scopeResolver.resolve("runtime-config", "clp-backend")).thenReturn(scope());
        when(authRefResolver.resolveForCurrentRequest()).thenReturn(AnalysisAiAuthRef.localToken(null));
        when(deterministicService.build(any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    var listener = invocation.getArgument(
                            3,
                            RuntimeConfigurationDeterministicContextListener.class
                    );
                    var context = deterministic();
                    listener.onSourceStarted();
                    listener.onSourceCompleted();
                    listener.onParseStarted();
                    listener.onParseCompleted();
                    listener.onDiffStarted();
                    listener.onDiffCompleted(context);
                    return context;
                });
        when(promptService.prepare(any(), any(), any()))
                .thenReturn(new RuntimeConfigurationPromptPreparation(
                        "safe prompt",
                        Map.of("manifest.json", "{}"),
                        List.of()
                ));
        when(aiRunner.run(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RuntimeConfigurationAiRunResult(completedAssessment(), null));
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

        assertEquals("COMPLETED", completed.status());
        assertEquals(
                List.of("SOURCE", "PARSE", "DIFF", "AI"),
                completed.steps().stream().map(step -> step.code()).toList()
        );
        assertEquals(RuntimeConfigurationVerificationStatus.NO_BLOCKING_ANOMALIES,
                completed.result().status());
        assertNotNull(completed.result().deterministicResult());
        assertFalse(completed.imported());
        verify(deepService, never()).build(any(), anyString(), anyString(), any(), any(), any());
        verify(persistence, org.mockito.Mockito.atLeast(6)).persistRunSnapshot(any());
    }

    @Test
    void shouldCreateIndependentJobs() {
        var first = service.startJob(request());
        var second = service.startJob(request());

        assertNotEquals(first.jobId(), second.jobId());
    }

    @Test
    void shouldRejectUnknownJob() {
        assertThrows(
                RuntimeConfigurationVerificationJobNotFoundException.class,
                () -> service.getJob("missing-job")
        );
    }

    @Test
    void shouldRejectInvalidScopeBeforeCreatingJob() {
        when(scopeResolver.resolve("runtime-config", "clp-backend")).thenThrow(
                RuntimeConfigurationScopeException.configurationDirectoryMissing("clp-backend")
        );

        var exception = assertThrows(
                RuntimeConfigurationScopeException.class,
                () -> service.startJob(request())
        );

        assertEquals("RUNTIME_CONFIGURATION_DIRECTORY_MISSING", exception.code());
        assertEquals(0, executor.size());
    }

    @Test
    void shouldCompleteDeepLifecycle() {
        var deep = deepContext(RuntimeConfigurationDeepContextStatus.COMPLETE, List.of());
        stubDeep(deep);

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());

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
                completed.steps().stream().map(step -> step.code()).toList()
        );
        assertEquals(deep, completed.result().deepAnalysis());
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

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, completed.result().status());
        assertNotNull(completed.result().deterministicResult());
        assertTrue(completed.result().visibilityLimits().contains("Code ref was not confirmed."));
    }

    @Test
    void shouldKeepDeterministicResultWhenDeepFails() {
        when(deepService.build(
                any(), anyString(), anyString(), any(), any(), any()
        )).thenThrow(new IllegalStateException("do-not-expose-deep-detail"));

        var created = service.startJob(deepRequest());
        executor.runNext();
        var completed = service.getJob(created.jobId());

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, completed.result().status());
        assertNotNull(completed.result().deterministicResult());
        assertFalse(completed.toString().contains("do-not-expose-deep-detail"));
    }

    @Test
    void shouldKeepDeterministicResultWhenAiFails() {
        when(aiRunner.run(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("do-not-expose-ai-detail"));
        when(assessmentService.assess(any(), any(), any(), any(), any(), any()))
                .thenReturn(incompleteAssessment());

        var created = service.startJob(request());
        executor.runNext();
        var completed = service.getJob(created.jobId());

        assertEquals("COMPLETED_WITH_LIMITATIONS", completed.status());
        assertEquals("RUNTIME_CONFIGURATION_AI_INCOMPLETE", completed.errorCode());
        assertEquals(RuntimeConfigurationVerificationStatus.INCOMPLETE, completed.result().status());
        assertNotNull(completed.result().deterministicResult());
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
                "clp-backend",
                "CLP Backend",
                "backend"
        );
    }

    static RuntimeConfigurationDeterministicContext deterministic() {
        return new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "clp-backend",
                "CLP Backend",
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

    static RuntimeConfigurationVerificationJobStartRequest request() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "clp-backend",
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    private static RuntimeConfigurationVerificationJobStartRequest deepRequest() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                "clp-backend",
                "dev1",
                "zt001",
                "release-42",
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
