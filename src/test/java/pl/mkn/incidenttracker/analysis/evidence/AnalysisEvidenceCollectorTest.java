package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.adapter.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.deployment.DeploymentContextResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalysisEvidenceCollectorTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    private final AnalysisEvidenceCollector analysisEvidenceCollector = new AnalysisEvidenceCollector(List.of(
            new ElasticLogEvidenceProvider(new TestElasticLogPort()),
            new DeploymentContextEvidenceProvider(deploymentContextResolver),
            new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
            new GitLabDeterministicEvidenceProvider(
                    mock(GitLabRepositoryPort.class),
                    gitLabProperties,
                    mock(GitLabSourceResolveService.class),
                    deploymentContextResolver
            )
    ));

    @Test
    void shouldSkipDynatraceEvidenceForDevEnvironment() {
        var context = analysisEvidenceCollector.collect("timeout-123");
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
        var context = analysisEvidenceCollector.collect("not-found");

        assertTrue(context.evidenceSections().isEmpty());
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("sample/runtime");
        return properties;
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

