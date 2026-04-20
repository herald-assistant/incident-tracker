package pl.mkn.incidenttracker.analysis.evidence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class AnalysisEvidenceCollector {

    private final ElasticLogEvidenceProvider elasticLogEvidenceProvider;
    private final DeploymentContextEvidenceProvider deploymentContextEvidenceProvider;
    private final DynatraceEvidenceProvider dynatraceEvidenceProvider;
    private final GitLabDeterministicEvidenceProvider gitLabDeterministicEvidenceProvider;
    private final OperationalContextEvidenceProvider operationalContextEvidenceProvider;
    private final TaskExecutor parallelEvidenceTaskExecutor;

    public AnalysisEvidenceCollector(
            ElasticLogEvidenceProvider elasticLogEvidenceProvider,
            DeploymentContextEvidenceProvider deploymentContextEvidenceProvider,
            DynatraceEvidenceProvider dynatraceEvidenceProvider,
            GitLabDeterministicEvidenceProvider gitLabDeterministicEvidenceProvider,
            OperationalContextEvidenceProvider operationalContextEvidenceProvider,
            @Qualifier("analysisEvidenceTaskExecutor") TaskExecutor parallelEvidenceTaskExecutor
    ) {
        this.elasticLogEvidenceProvider = elasticLogEvidenceProvider;
        this.deploymentContextEvidenceProvider = deploymentContextEvidenceProvider;
        this.dynatraceEvidenceProvider = dynatraceEvidenceProvider;
        this.gitLabDeterministicEvidenceProvider = gitLabDeterministicEvidenceProvider;
        this.operationalContextEvidenceProvider = operationalContextEvidenceProvider;
        this.parallelEvidenceTaskExecutor = parallelEvidenceTaskExecutor;
    }

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
        var dynatraceFuture = submitProvider(dynatraceEvidenceProvider, sharedContext);
        var gitLabFuture = submitProvider(gitLabDeterministicEvidenceProvider, sharedContext);

        var updatedContext = runSubmittedProvider(
                dynatraceEvidenceProvider,
                sharedContext,
                context,
                dynatraceFuture,
                listener
        );
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
        return CompletableFuture.supplyAsync(() -> provider.collect(context), parallelEvidenceTaskExecutor);
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
