package pl.mkn.incidenttracker.analysis.deployment;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeploymentContextEvidenceProviderTest {

    private final DeploymentContextEvidenceProvider provider =
            new DeploymentContextEvidenceProvider(new DeploymentContextResolver());

    @Test
    void shouldResolveDeploymentContextFromElasticEvidence() {
        var baseContext = AnalysisContext.initialize("timeout-123");
        var elasticSection = new ElasticLogEvidenceProvider(new TestElasticLogPort()).collect(baseContext);
        var context = baseContext.withSection(elasticSection);

        var section = provider.collect(context);

        assertEquals("deployment-context", section.provider());
        assertEquals("resolved-deployment", section.category());
        assertEquals(2, section.items().size());

        var lookupItem = section.items().get(0);
        assertEquals("Wejście do lookupu Dynatrace", lookupItem.title());
        var lookupAttributes = attributesByName(lookupItem);
        assertEquals("2026-04-11T20:57:33.285Z", lookupAttributes.get("incidentStart"));
        assertEquals("2026-04-11T20:57:33.290Z", lookupAttributes.get("incidentEnd"));
        assertEquals("ns", lookupAttributes.get("namespaces"));
        assertEquals("pod", lookupAttributes.get("podNames"));
        assertEquals("backend", lookupAttributes.get("containerNames"));
        assertEquals("svc", lookupAttributes.get("serviceNames"));

        var deploymentItem = section.items().get(1);
        var deploymentAttributes = attributesByName(deploymentItem);
        assertEquals("dev3", deploymentAttributes.get("environment"));
        assertEquals("dev/atlas", deploymentAttributes.get("branch"));
        assertEquals("backend", deploymentAttributes.get("projectNameHint"));
        assertEquals("backend", deploymentAttributes.get("containerName"));
    }

    @Test
    void shouldResolveSpecialNamespaceDeploymentContextForUatEnvironment() {
        var baseContext = AnalysisContext.initialize("uat-incident-123");
        var elasticSection = new ElasticLogEvidenceProvider(uatElasticLogPort()).collect(baseContext);
        var context = baseContext.withSection(elasticSection);

        var section = provider.collect(context);

        assertEquals("deployment-context", section.provider());
        assertEquals("resolved-deployment", section.category());
        assertEquals(2, section.items().size());

        var lookupItem = section.items().get(0);
        var lookupAttributes = attributesByName(lookupItem);
        assertEquals("2026-04-11T20:57:33.285Z", lookupAttributes.get("incidentStart"));
        assertEquals("2026-04-11T20:57:33.285Z", lookupAttributes.get("incidentEnd"));
        assertEquals("tenant-alpha-main-uat2", lookupAttributes.get("namespaces"));
        assertEquals("backend-uat2", lookupAttributes.get("podNames"));
        assertEquals("backend", lookupAttributes.get("containerNames"));
        assertEquals("case-evaluation-service", lookupAttributes.get("serviceNames"));

        var deploymentItem = section.items().get(1);
        var deploymentAttributes = attributesByName(deploymentItem);
        assertEquals("uat2", deploymentAttributes.get("environment"));
        assertEquals("release-candidate", deploymentAttributes.get("branch"));
        assertEquals("backend", deploymentAttributes.get("projectNameHint"));
        assertEquals("backend", deploymentAttributes.get("containerName"));
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

    private static ElasticLogPort uatElasticLogPort() {
        return new ElasticLogPort() {
            @Override
            public List<ElasticLogEntry> findLogEntries(String correlationId) {
                return List.of(new ElasticLogEntry(
                        "2026-04-11T20:57:33.285Z",
                        "ERROR",
                        "case-evaluation-service",
                        "c.e.synthetic.workflow.WorkflowApiExceptionHandler",
                        "Gateway timeout while calling downstream service",
                        null,
                        "main",
                        null,
                        "tenant-alpha-main-uat2",
                        "backend-uat2",
                        "backend",
                        "reg.local/tenant-alpha-main-uat2/backend:60-release-candidate-123",
                        "test-index",
                        correlationId,
                        false,
                        false
                ));
            }

            @Override
            public pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult searchLogsByCorrelationId(
                    String correlationId
            ) {
                var entries = findLogEntries(correlationId);
                return new pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult(
                        correlationId,
                        "test",
                        entries.size(),
                        entries.size(),
                        entries.size(),
                        0,
                        false,
                        entries,
                        "OK"
                );
            }
        };
    }

}

