package pl.mkn.incidenttracker.analysis.evidence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisEvidenceCollector {

    private final ElasticLogEvidenceProvider elasticLogEvidenceProvider;
    private final DeploymentContextEvidenceProvider deploymentContextEvidenceProvider;
    private final DynatraceEvidenceProvider dynatraceEvidenceProvider;
    private final GitLabDeterministicEvidenceProvider gitLabDeterministicEvidenceProvider;
    private final OperationalContextEvidenceProvider operationalContextEvidenceProvider;

    public AnalysisContext collect(String correlationId, AnalysisEvidenceCollectionListener listener) {
        var context = AnalysisContext.initialize(correlationId);
        context = runProvider(elasticLogEvidenceProvider, context, listener);
        context = runProvider(deploymentContextEvidenceProvider, context, listener);
        context = runProvider(dynatraceEvidenceProvider, context, listener);
        context = runProvider(gitLabDeterministicEvidenceProvider, context, listener);
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
        try {
            AnalysisEvidenceSection section = provider.collect(context);
            var updatedContext = context.withSection(section);
            listener.onProviderCompleted(provider, section, updatedContext);
            return updatedContext;
        } catch (RuntimeException exception) {
            listener.onProviderFailed(provider, exception, context);
            throw exception;
        }
    }

}
