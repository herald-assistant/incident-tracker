package pl.mkn.incidenttracker.features.incidentanalysis.evidence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisEvidenceCollector {

    private final ElasticLogEvidenceProvider elasticLogEvidenceProvider;
    private final DeploymentContextEvidenceProvider deploymentContextEvidenceProvider;
    private final DynatraceEvidenceProvider dynatraceEvidenceProvider;
    private final GitLabDeterministicEvidenceProvider gitLabDeterministicEvidenceProvider;
    private final OperationalContextEvidenceProvider operationalContextEvidenceProvider;
    private final TaskExecutor analysisEvidenceTaskExecutor;

    public AnalysisContext collect(String correlationId, AnalysisEvidenceCollectionListener listener) {
        var context = AnalysisContext.initialize(correlationId);
        context = runProvider(elasticLogEvidenceProvider, context, listener);
        context = runProvider(deploymentContextEvidenceProvider, context, listener);
        context = runParallelEnrichmentProviders(context, listener);
        context = runProvider(operationalContextEvidenceProvider, context, listener);
        return context;
    }

    public List<AnalysisEvidenceProviderDescriptor> providerDescriptors() {
        return List.of(
                elasticLogEvidenceProvider.descriptor(),
                deploymentContextEvidenceProvider.descriptor(),
                dynatraceEvidenceProvider.descriptor(),
                gitLabDeterministicEvidenceProvider.descriptor(),
                operationalContextEvidenceProvider.descriptor()
        );
    }

    private AnalysisContext runProvider(
            AnalysisEvidenceProvider provider,
            AnalysisContext context,
            AnalysisEvidenceCollectionListener listener
    ) {
        listener.onProviderStarted(provider, context);
        AnalysisEvidenceSection section = provider.collect(context);
        var updatedContext = context.withSection(section);
        listener.onProviderCompleted(provider, section, updatedContext);
        return updatedContext;
    }

    private AnalysisContext runParallelEnrichmentProviders(
            AnalysisContext context,
            AnalysisEvidenceCollectionListener listener
    ) {
        var sharedContext = context;
        var gitLabFuture = submitProvider(gitLabDeterministicEvidenceProvider, sharedContext);
        var updatedContext = runProvider(dynatraceEvidenceProvider, sharedContext, listener);
        return runSubmittedProvider(
                gitLabDeterministicEvidenceProvider,
                sharedContext,
                updatedContext,
                gitLabFuture,
                listener
        );
    }

    private CompletableFuture<AnalysisEvidenceSection> submitProvider(
            AnalysisEvidenceProvider provider,
            AnalysisContext context
    ) {
        try {
            return CompletableFuture.supplyAsync(() -> provider.collect(context), analysisEvidenceTaskExecutor);
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Parallel evidence executor saturated, falling back to inline execution for step={} correlationId={}",
                    provider.stepCode(),
                    context.correlationId()
            );
            return CompletableFuture.completedFuture(provider.collect(context));
        }
    }

    private AnalysisContext runSubmittedProvider(
            AnalysisEvidenceProvider provider,
            AnalysisContext providerContext,
            AnalysisContext mergeContext,
            CompletableFuture<AnalysisEvidenceSection> future,
            AnalysisEvidenceCollectionListener listener
    ) {
        listener.onProviderStarted(provider, providerContext);
        var section = awaitSection(provider, future);
        var updatedContext = mergeContext.withSection(section);
        listener.onProviderCompleted(provider, section, updatedContext);
        return updatedContext;
    }

    private AnalysisEvidenceSection awaitSection(
            AnalysisEvidenceProvider provider,
            CompletableFuture<AnalysisEvidenceSection> future
    ) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new IllegalStateException(
                    "Evidence provider failed: " + provider.stepCode(),
                    exception.getCause() != null ? exception.getCause() : exception
            );
        }
    }

}
