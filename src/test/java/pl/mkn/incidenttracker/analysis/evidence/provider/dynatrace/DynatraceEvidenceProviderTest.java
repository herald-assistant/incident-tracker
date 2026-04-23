package pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentEvidence;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynatraceEvidenceProviderTest {

    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    @Test
    void shouldSkipDynatraceLookupForDevEnvironment() {
        var dynatracePort = mock(DynatraceIncidentPort.class);
        var provider = new DynatraceEvidenceProvider(dynatracePort, deploymentContextResolver);
        var context = contextFrom(new pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort(), "timeout-123");

        var section = provider.collect(context);

        assertEquals("dynatrace", section.provider());
        assertEquals("runtime-signals", section.category());
        assertEquals(1, section.items().size());
        assertEquals("Dynatrace collection status", section.items().get(0).title());
        assertEquals("SKIPPED", attributeValue(section.items().get(0), "collectionStatus"));
        assertTrue(attributeValue(section.items().get(0), "collectionReason").contains("dev environment"));
        verify(dynatracePort, never()).loadIncidentEvidence(any());
    }

    @Test
    void shouldCollectDynatraceEvidenceForUatEnvironment() {
        var dynatracePort = mock(DynatraceIncidentPort.class);
        var provider = new DynatraceEvidenceProvider(dynatracePort, deploymentContextResolver);
        var context = contextFrom(uatElasticLogPort(), "uat-incident-123");

        when(dynatracePort.loadIncidentEvidence(any())).thenReturn(new DynatraceIncidentEvidence(
                List.of(new DynatraceIncidentEvidence.ServiceMatch(
                        "SVC",
                        "svc",
                        300,
                        List.of("ns"),
                        List.of("pod"),
                        List.of("ctr"),
                        List.of("svc")
                )),
                List.of(new DynatraceIncidentEvidence.ProblemSummary(
                        "PROBLEM-123",
                        "P-123",
                        "Gateway timeout on backend",
                        "SERVICE",
                        "ERROR",
                        "OPEN",
                        Instant.parse("2026-04-11T20:57:00Z"),
                        Instant.parse("2026-04-11T21:06:00Z"),
                        "SVC",
                        "svc",
                        List.of("svc"),
                        List.of("api"),
                        List.of(
                                new DynatraceIncidentEvidence.ProblemEvidence(
                                        "EVENT",
                                        "db",
                                        "db",
                                        "db",
                                        true,
                                        "DATABASE_CONNECTION_FAILURE",
                                        null,
                                        null,
                                        null,
                                        null,
                                        Instant.parse("2026-04-11T20:57:30Z"),
                                        Instant.parse("2026-04-11T20:59:00Z")
                                ),
                                new DynatraceIncidentEvidence.ProblemEvidence(
                                        "AVAILABILITY_EVIDENCE",
                                        "down",
                                        "svc",
                                        "svc",
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Instant.parse("2026-04-11T20:58:00Z"),
                                        Instant.parse("2026-04-11T21:01:00Z")
                                ),
                                new DynatraceIncidentEvidence.ProblemEvidence(
                                        "EVENT",
                                        "queue",
                                        "mq",
                                        "mq",
                                        false,
                                        "QUEUE_BACKLOG",
                                        null,
                                        null,
                                        null,
                                        null,
                                        Instant.parse("2026-04-11T20:59:00Z"),
                                        Instant.parse("2026-04-11T21:02:00Z")
                                )
                        )
                )),
                List.of(
                        new DynatraceIncidentEvidence.MetricSummary(
                                "SVC",
                                "svc",
                                "builtin:service.response.time:percentile(95)",
                                "service.response.time.p95",
                                "ms",
                                "1m",
                                Instant.parse("2026-04-11T20:57:00Z"),
                                Instant.parse("2026-04-11T21:06:00Z"),
                                5,
                                85.0,
                                1840.0,
                                620.0,
                                1710.0
                        ),
                        new DynatraceIncidentEvidence.MetricSummary(
                                "SVC",
                                "svc",
                                "builtin:service.successes.server.rate:avg",
                                "service.success.rate",
                                "%",
                                "1m",
                                Instant.parse("2026-04-11T20:57:00Z"),
                                Instant.parse("2026-04-11T21:06:00Z"),
                                5,
                                100.0,
                                100.0,
                                100.0,
                                100.0
                        )
                )
        ));

        var section = provider.collect(context);

        assertEquals("dynatrace", section.provider());
        assertEquals("runtime-signals", section.category());
        var collectionItem = section.items().stream()
                .filter(item -> "Dynatrace collection status".equals(item.title()))
                .findFirst()
                .orElseThrow();
        assertEquals("COLLECTED", attributeValue(collectionItem, "collectionStatus"));
        assertEquals("MATCHED", attributeValue(collectionItem, "correlationStatus"));

        var runtimeView = DynatraceRuntimeEvidenceView.from(section);
        assertEquals(DynatraceRuntimeEvidenceView.CollectionStatus.COLLECTED, runtimeView.collectionStatus().status());
        assertEquals(DynatraceRuntimeEvidenceView.CorrelationStatus.MATCHED, runtimeView.collectionStatus().correlationStatus());
        assertEquals(1, runtimeView.componentStatuses().size());
        assertEquals("svc", runtimeView.componentStatuses().get(0).componentName());
        assertEquals(
                DynatraceRuntimeEvidenceView.ComponentSignalStatus.SIGNALS_PRESENT,
                runtimeView.componentStatuses().get(0).signalStatus()
        );
        assertEquals("P-123", runtimeView.componentStatuses().get(0).problemDisplayId());

        var componentItem = section.items().stream()
                .filter(item -> item.title().contains("Dynatrace correlated component"))
                .findFirst()
                .orElseThrow();
        assertEquals("svc", attributeValue(componentItem, "componentName"));
        assertEquals("SIGNALS_PRESENT", attributeValue(componentItem, "componentSignalStatus"));
        assertEquals("P-123", attributeValue(componentItem, "problemDisplayId"));
        assertTrue(attributeValue(componentItem, "summary").contains("db"));

        var serviceMatchItem = section.items().stream()
                .filter(item -> item.title().contains("Dynatrace matched service"))
                .findFirst()
                .orElseThrow();
        assertEquals("svc", attributeValue(serviceMatchItem, "componentName"));

        var problemItem = section.items().stream()
                .filter(item -> item.title().contains("Dynatrace problem"))
                .findFirst()
                .orElseThrow();
        assertTrue(problemItem.title().contains("P-123"));
        assertEquals(
                "database-connectivity, availability, messaging",
                attributeValue(problemItem, "signalCategories")
        );
        assertTrue(attributeValue(problemItem, "correlationHighlights").contains("db"));
        assertTrue(attributeValue(problemItem, "correlationHighlights").contains("DATABASE_CONNECTION_FAILURE"));
        assertTrue(attributeValue(problemItem, "correlationHighlights").contains("queue"));

        var metricItem = section.items().stream()
                .filter(item -> item.title().contains("Dynatrace metric"))
                .findFirst()
                .orElse(null);
        assertNotNull(metricItem);
        assertEquals("service.response.time.p95", attributeValue(metricItem, "metricLabel"));
        assertEquals("svc", attributeValue(metricItem, "componentName"));
        assertEquals(
                1,
                section.items().stream().filter(item -> item.title().contains("Dynatrace metric")).count()
        );
        verify(dynatracePort).loadIncidentEvidence(any());
    }

    @Test
    void shouldExposeUnavailableStatusWhenDynatraceCallFails() {
        var dynatracePort = mock(DynatraceIncidentPort.class);
        var provider = new DynatraceEvidenceProvider(dynatracePort, deploymentContextResolver);
        var context = contextFrom(uatElasticLogPort(), "uat-incident-502");

        when(dynatracePort.loadIncidentEvidence(any())).thenThrow(
                new IllegalStateException("Dynatrace request failed for /api/v2/problems with status 502")
        );

        var section = provider.collect(context);

        assertEquals("dynatrace", section.provider());
        assertEquals("runtime-signals", section.category());
        assertEquals(1, section.items().size());
        assertEquals("UNAVAILABLE", attributeValue(section.items().get(0), "collectionStatus"));
        assertEquals(
                "Dynatrace API request failed with HTTP 502",
                attributeValue(section.items().get(0), "collectionReason")
        );
        assertTrue(attributeValue(section.items().get(0), "interpretation").contains("lack of data"));

        var runtimeView = DynatraceRuntimeEvidenceView.from(section);
        assertEquals(DynatraceRuntimeEvidenceView.CollectionStatus.UNAVAILABLE, runtimeView.collectionStatus().status());
        assertEquals(
                "Dynatrace API request failed with HTTP 502",
                runtimeView.collectionStatus().reason()
        );
        assertTrue(runtimeView.componentStatuses().isEmpty());
    }

    private static String attributeValue(pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem item, String name) {
        return item.attributes().stream()
                .filter(attribute -> attribute.name().equals(name))
                .map(pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute::value)
                .findFirst()
                .orElse(null);
    }

    private static AnalysisContext contextFrom(ElasticLogPort elasticLogPort, String correlationId) {
        var baseContext = AnalysisContext.initialize(correlationId);
        var elasticSection = new ElasticLogEvidenceProvider(elasticLogPort).collect(baseContext);
        return baseContext.withSection(elasticSection);
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

