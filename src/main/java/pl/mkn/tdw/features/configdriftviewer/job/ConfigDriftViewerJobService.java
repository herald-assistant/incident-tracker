package pl.mkn.tdw.features.configdriftviewer.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.error.ConfigDriftViewerJobNotFoundException;
import pl.mkn.tdw.features.configdriftviewer.job.localworkspace.ConfigDriftViewerLocalRunPersistence;
import pl.mkn.tdw.features.configdriftviewer.job.state.ConfigDriftViewerBatchJobState;
import pl.mkn.tdw.features.configdriftviewer.job.state.ConfigDriftViewerJobState;
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
public class ConfigDriftViewerJobService {

    private final ConfigDriftViewerComponentRunner componentRunner;
    private final ConfigDriftViewerLocalRunPersistence localRunPersistence;
    private final TaskExecutor applicationTaskExecutor;
    private final ConfigDriftViewerComponentExecutor configDriftViewerComponentExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final Map<String, ConfigDriftViewerBatchJobState> jobs = new ConcurrentHashMap<>();

    public ConfigDriftViewerJobStateSnapshot startJob(
            ConfigDriftViewerJobStartRequest request
    ) {
        var authRef = resolveAiAuth(request);
        var jobId = UUID.randomUUID().toString();
        var job = new ConfigDriftViewerBatchJobState(jobId, request);
        jobs.put(jobId, job);
        persistRunSnapshot(job);
        var immediateSnapshot = job.snapshot();
        applicationTaskExecutor.execute(() -> runComponentsInParallel(job, request, authRef));
        return immediateSnapshot;
    }

    public ConfigDriftViewerJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new ConfigDriftViewerJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private void runComponentsInParallel(
            ConfigDriftViewerBatchJobState job,
            ConfigDriftViewerJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        var componentFutures = new ArrayList<CompletableFuture<Void>>(job.components().size());
        for (var component : job.components()) {
            try {
                componentFutures.add(configDriftViewerComponentExecutor.runAsync(
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
            ConfigDriftViewerJobState component,
            ConfigDriftViewerBatchJobState job
    ) {
        component.markFailed(
                "RUNTIME_CONFIGURATION_COMPONENT_EXECUTION_FAILED",
                "Component comparison did not complete. Retry the verification."
        );
        persistRunSnapshot(job);
    }

    private AnalysisAiAuthRef resolveAiAuth(ConfigDriftViewerJobStartRequest request) {
        if (request.mode() != ConfigDriftViewerMode.DEEP) {
            return null;
        }
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));
        return authRef;
    }

    private void persistRunSnapshot(ConfigDriftViewerBatchJobState job) {
        synchronized (job) {
            var snapshot = job.snapshot();
            try {
                localRunPersistence.persistRunSnapshot(snapshot);
            } catch (RuntimeException exception) {
                log.warn(
                        "Config Drift Viewer snapshot persistence failed jobId={} failureType={}",
                        snapshot.jobId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
