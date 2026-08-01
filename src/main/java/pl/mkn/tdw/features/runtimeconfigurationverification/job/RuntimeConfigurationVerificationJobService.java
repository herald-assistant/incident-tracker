package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationJobNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace.RuntimeConfigurationVerificationLocalRunPersistence;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.state.RuntimeConfigurationVerificationBatchJobState;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.state.RuntimeConfigurationVerificationJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationJobService {

    private final RuntimeConfigurationComponentRunner componentRunner;
    private final RuntimeConfigurationVerificationLocalRunPersistence localRunPersistence;
    private final TaskExecutor applicationTaskExecutor;
    private final RuntimeConfigurationComponentExecutor runtimeConfigurationComponentExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final Map<String, RuntimeConfigurationVerificationBatchJobState> jobs = new ConcurrentHashMap<>();

    public RuntimeConfigurationVerificationJobStateSnapshot startJob(
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        var authRef = resolveAiAuth(request);
        var jobId = UUID.randomUUID().toString();
        var job = new RuntimeConfigurationVerificationBatchJobState(jobId, request);
        jobs.put(jobId, job);
        persistRunSnapshot(job);
        var immediateSnapshot = job.snapshot();
        applicationTaskExecutor.execute(() -> runComponentsInParallel(job, request, authRef));
        return immediateSnapshot;
    }

    public RuntimeConfigurationVerificationJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new RuntimeConfigurationVerificationJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private void runComponentsInParallel(
            RuntimeConfigurationVerificationBatchJobState job,
            RuntimeConfigurationVerificationJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        var componentFutures = new ArrayList<CompletableFuture<Void>>(job.components().size());
        for (var component : job.components()) {
            try {
                componentFutures.add(runtimeConfigurationComponentExecutor.runAsync(
                        () -> componentRunner.run(
                                component,
                                request.forSystem(component.systemId()),
                                authRef,
                                () -> persistRunSnapshot(job)
                        )
                ).handle((ignored, failure) -> {
                    if (failure != null) {
                        markComponentExecutionFailed(component, job);
                    }
                    return null;
                }));
            } catch (RuntimeException exception) {
                markComponentExecutionFailed(component, job);
            }
        }
        CompletableFuture.allOf(componentFutures.toArray(CompletableFuture[]::new)).join();
        persistRunSnapshot(job);
    }

    private void markComponentExecutionFailed(
            RuntimeConfigurationVerificationJobState component,
            RuntimeConfigurationVerificationBatchJobState job
    ) {
        component.markFailed(
                "RUNTIME_CONFIGURATION_COMPONENT_EXECUTION_FAILED",
                "Component comparison did not complete. Retry the verification."
        );
        persistRunSnapshot(job);
    }

    private AnalysisAiAuthRef resolveAiAuth(RuntimeConfigurationVerificationJobStartRequest request) {
        if (request.mode() != RuntimeConfigurationVerificationMode.DEEP) {
            return null;
        }
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));
        return authRef;
    }

    private void persistRunSnapshot(RuntimeConfigurationVerificationBatchJobState job) {
        synchronized (job) {
            var snapshot = job.snapshot();
            try {
                localRunPersistence.persistRunSnapshot(snapshot);
            } catch (RuntimeException exception) {
                log.warn(
                        "Runtime Configuration Verification snapshot persistence failed jobId={} failureType={}",
                        snapshot.jobId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
