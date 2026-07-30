package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiAssessmentService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiRunner;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportFactory;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationJobNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace.RuntimeConfigurationVerificationLocalRunPersistence;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.state.RuntimeConfigurationVerificationJobState;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationJobService {

    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final RuntimeConfigurationDeterministicContextService deterministicContextService;
    private final RuntimeConfigurationDeepContextService deepContextService;
    private final RuntimeConfigurationPromptPreparationService promptPreparationService;
    private final RuntimeConfigurationAiRunner aiRunner;
    private final RuntimeConfigurationAiAssessmentService assessmentService;
    private final RuntimeConfigurationReportFactory reportFactory;
    private final RuntimeConfigurationVerificationLocalRunPersistence localRunPersistence;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final Map<String, RuntimeConfigurationVerificationJobState> jobs = new ConcurrentHashMap<>();

    public RuntimeConfigurationVerificationJobStateSnapshot startJob(
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        var scope = scopeResolver.resolve(request.repositoryId(), request.systemId());
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));

        var jobId = UUID.randomUUID().toString();
        var job = new RuntimeConfigurationVerificationJobState(jobId, request);
        jobs.put(jobId, job);
        persistRunSnapshot(job);
        var immediateSnapshot = job.snapshot();
        applicationTaskExecutor.execute(() -> runJob(jobId, job, request, scope, authRef));
        return immediateSnapshot;
    }

    public RuntimeConfigurationVerificationJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new RuntimeConfigurationVerificationJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private void runJob(
            String jobId,
            RuntimeConfigurationVerificationJobState job,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationScope scope,
            AnalysisAiAuthRef authRef
    ) {
        try {
            var deterministic = deterministicContextService.build(
                    scope,
                    request.sourceBranch(),
                    request.targetBranch(),
                    deterministicListener(job)
            );
            var deep = buildDeepContext(job, request, deterministic);
            job.markAiStarted(null);
            persistRunSnapshot(job);

            String safePrompt = null;
            try {
                var preparation = promptPreparationService.prepare(request, deterministic, deep);
                safePrompt = preparation.prompt();
                job.markAiStarted(safePrompt);
                persistRunSnapshot(job);
                var aiRun = aiRunner.run(
                        jobId,
                        request,
                        deterministic,
                        deep,
                        preparation,
                        authRef,
                        section -> {
                            job.markAiToolEvidenceUpdated(section);
                            persistRunSnapshot(job);
                        },
                        event -> {
                            job.markAiActivity(event);
                            persistRunSnapshot(job);
                        }
                );
                job.markCompleted(deep, aiRun, safePrompt);
            } catch (RuntimeException exception) {
                log.warn(
                        "Runtime Configuration Verification AI failed jobId={} failureType={}",
                        jobId,
                        exception.getClass().getSimpleName()
                );
                var scaffold = reportFactory.createInitialReport(
                        "runtime-configuration-" + jobId,
                        request.mode(),
                        deterministic,
                        deep
                );
                var fallback = assessmentService.assess(
                        null,
                        request.mode(),
                        deterministic,
                        deep,
                        scaffold,
                        null
                );
                job.markAiFailed(deep, fallback, safePrompt);
            }
            persistRunSnapshot(job);
        } catch (RuntimeException exception) {
            log.warn(
                    "Runtime Configuration Verification failed jobId={} failureType={}",
                    jobId,
                    exception.getClass().getSimpleName()
            );
            job.markFailed(
                    "RUNTIME_CONFIGURATION_VERIFICATION_FAILED",
                    "Configuration verification did not complete. Check access and configuration coverage, then retry."
            );
            persistRunSnapshot(job);
        }
    }

    private RuntimeConfigurationDeepContext buildDeepContext(
            RuntimeConfigurationVerificationJobState job,
            RuntimeConfigurationVerificationJobStartRequest request,
            pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                    .RuntimeConfigurationDeterministicContext deterministic
    ) {
        if (request.mode() != RuntimeConfigurationVerificationMode.DEEP) {
            return null;
        }
        try {
            return deepContextService.build(
                    request.mode(),
                    request.repositoryId(),
                    request.systemId(),
                    request.codeRef(),
                    deterministic,
                    deepListener(job)
            ).orElse(null);
        } catch (RuntimeException exception) {
            log.warn(
                    "Runtime Configuration Verification DEEP enrichment failed systemId={} failureType={}",
                    request.systemId(),
                    exception.getClass().getSimpleName()
            );
            job.markDeepFailed();
            persistRunSnapshot(job);
            return null;
        }
    }

    private RuntimeConfigurationDeterministicContextListener deterministicListener(
            RuntimeConfigurationVerificationJobState job
    ) {
        return new RuntimeConfigurationDeterministicContextListener() {
            @Override
            public void onSourceStarted() {
                job.markSourceStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onSourceCompleted() {
                job.markSourceCompleted();
                persistRunSnapshot(job);
            }

            @Override
            public void onParseStarted() {
                job.markParseStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onParseCompleted() {
                job.markParseCompleted();
                persistRunSnapshot(job);
            }

            @Override
            public void onDiffStarted() {
                job.markDiffStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onDiffCompleted(
                    pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                            .RuntimeConfigurationDeterministicContext context
            ) {
                job.markDiffCompleted(context);
                persistRunSnapshot(job);
            }
        };
    }

    private RuntimeConfigurationDeepContextListener deepListener(
            RuntimeConfigurationVerificationJobState job
    ) {
        return new RuntimeConfigurationDeepContextListener() {
            @Override
            public void onOperationalContextStarted() {
                job.markOperationalContextStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onOperationalContextCompleted() {
                job.markOperationalContextCompleted();
                persistRunSnapshot(job);
            }

            @Override
            public void onCodeGroundingStarted() {
                job.markCodeGroundingStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onCodeGroundingCompleted() {
                job.markCodeGroundingCompleted();
                persistRunSnapshot(job);
            }

            @Override
            public void onOwnershipStarted() {
                job.markOwnershipStarted();
                persistRunSnapshot(job);
            }

            @Override
            public void onOwnershipCompleted(RuntimeConfigurationDeepContext context) {
                job.markOwnershipCompleted(context);
                persistRunSnapshot(job);
            }
        };
    }

    private void persistRunSnapshot(RuntimeConfigurationVerificationJobState job) {
        try {
            localRunPersistence.persistRunSnapshot(job.snapshot());
        } catch (RuntimeException exception) {
            log.warn(
                    "Runtime Configuration Verification snapshot persistence failed jobId={} failureType={}",
                    job.snapshot().jobId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
