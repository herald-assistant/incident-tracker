package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogLoader;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalysisEvidenceCollectorTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    private final AnalysisEvidenceCollector analysisEvidenceCollector = new AnalysisEvidenceCollector(
            new ElasticLogEvidenceProvider(new TestElasticLogPort()),
            new DeploymentContextEvidenceProvider(deploymentContextResolver),
            new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
            new GitLabDeterministicEvidenceProvider(
                    mock(GitLabRepositoryPort.class),
                    gitLabProperties,
                    mock(GitLabSourceResolveService.class),
                    deploymentContextResolver
            ),
            disabledOperationalContextEvidenceProvider()
    );

    @Test
    void shouldSkipDynatraceEvidenceForDevEnvironment() {
        var context = analysisEvidenceCollector.collect("timeout-123", AnalysisEvidenceCollectionListener.NO_OP);
        var evidenceSections = context.evidenceSections();

        assertEquals(2, evidenceSections.size());
        assertEquals("timeout-123", context.correlationId());

        var elasticSection = evidenceSections.get(0);
        assertEquals("elasticsearch", elasticSection.provider());
        assertEquals("logs", elasticSection.category());
        assertEquals("ERROR svc log entry", elasticSection.items().get(0).title());
        assertEquals("timestamp", elasticSection.items().get(0).attributes().get(0).name());
        assertEquals("2026-04-11T20:57:33.285Z", elasticSection.items().get(0).attributes().get(0).value());
        assertEquals("level", elasticSection.items().get(0).attributes().get(1).name());
        assertEquals("ERROR", elasticSection.items().get(0).attributes().get(1).value());

        var deploymentSection = evidenceSections.get(1);
        assertEquals("deployment-context", deploymentSection.provider());
        assertEquals("resolved-deployment", deploymentSection.category());
        assertEquals("Wejście do lookupu Dynatrace", deploymentSection.items().get(0).title());
        var deploymentAttributes = attributesByName(deploymentSection.items().get(1));
        assertEquals("dev3", deploymentAttributes.get("environment"));
        assertEquals("dev/atlas", deploymentAttributes.get("branch"));
        assertEquals("backend", deploymentAttributes.get("projectNameHint"));
    }

    @Test
    void shouldReturnEmptyListWhenNoEvidenceProviderHasData() {
        var context = analysisEvidenceCollector.collect("not-found", AnalysisEvidenceCollectionListener.NO_OP);

        assertTrue(context.evidenceSections().isEmpty());
    }

    @Test
    void shouldExposeProviderDescriptorsInExplicitPipelineOrder() {
        var descriptors = analysisEvidenceCollector.providerDescriptors();

        assertEquals(5, descriptors.size());
        assertEquals("ELASTICSEARCH_LOGS", descriptors.get(0).stepCode());
        assertEquals("DEPLOYMENT_CONTEXT", descriptors.get(1).stepCode());
        assertEquals("DYNATRACE_RUNTIME_SIGNALS", descriptors.get(2).stepCode());
        assertEquals("GITLAB_RESOLVED_CODE", descriptors.get(3).stepCode());
        assertEquals("OPERATIONAL_CONTEXT", descriptors.get(4).stepCode());
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("sample/runtime");
        return properties;
    }

    private static OperationalContextEvidenceProvider disabledOperationalContextEvidenceProvider() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(false);
        return new OperationalContextEvidenceProvider(
                properties,
                new OperationalContextCatalogLoader(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );
    }

    private static Map<String, String> attributesByName(AnalysisEvidenceItem item) {
        return item.attributes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value(),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

}
