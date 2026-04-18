package pl.mkn.incidenttracker.analysis.deployment;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

import java.util.List;

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
        assertEquals(1, section.items().size());
        assertEquals("dev3", section.items().get(0).attributes().get(0).value());
        assertEquals("dev/atlas", section.items().get(0).attributes().get(1).value());
        assertEquals("backend", section.items().get(0).attributes().get(2).value());
    }

    @Test
    void shouldResolveSpecialNamespaceDeploymentContextForUatEnvironment() {
        var baseContext = AnalysisContext.initialize("uat-incident-123");
        var elasticSection = new ElasticLogEvidenceProvider(uatElasticLogPort()).collect(baseContext);
        var context = baseContext.withSection(elasticSection);

        var section = provider.collect(context);

        assertEquals("deployment-context", section.provider());
        assertEquals("resolved-deployment", section.category());
        assertEquals(1, section.items().size());
        assertEquals("uat2", section.items().get(0).attributes().get(0).value());
        assertEquals("release-candidate", section.items().get(0).attributes().get(1).value());
        assertEquals("backend", section.items().get(0).attributes().get(2).value());
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

