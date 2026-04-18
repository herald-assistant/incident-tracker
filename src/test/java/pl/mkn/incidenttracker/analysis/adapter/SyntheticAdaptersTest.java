package pl.mkn.incidenttracker.analysis.adapter;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentQuery;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchQuery;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticAdaptersTest {

    @Test
    void shouldReturnStructuredTimeoutEvidence() {
        var elasticAdapter = new TestElasticLogPort();
        var dynatraceAdapter = new TestDynatraceIncidentPort();
        var gitLabAdapter = new TestGitLabRepositoryPort();

        var logEntries = elasticAdapter.findLogEntries("timeout-123");
        var incidentEvidence = dynatraceAdapter.loadIncidentEvidence(new DynatraceIncidentQuery(
                "timeout-123",
                Instant.parse("2026-04-11T20:57:33.285Z"),
                Instant.parse("2026-04-11T20:57:33.290Z"),
                List.of("tenant-alpha-main-dev3"),
                List.of("backend-65df9ffbbb-79p7j"),
                List.of("backend"),
                List.of("case-evaluation-service")
        ));
        var fileCandidates = gitLabAdapter.searchCandidateFiles(new GitLabRepositorySearchQuery(
                "timeout-123",
                "sample/runtime",
                "main",
                List.of("billing-service", "catalog-service"),
                List.of("GET /inventory", "POST /payment"),
                List.of("timeout", "inventory", "payment")
        ));

        assertEquals(2, logEntries.size());
        assertEquals("ERROR", logEntries.get(0).level());
        assertEquals("svc", logEntries.get(0).serviceName());
        assertEquals("c.e.s.response.TimeoutHandler", logEntries.get(0).className());
        assertTrue(logEntries.get(0).message().contains("timed out"));

        var elasticSearchResult = elasticAdapter.searchLogsByCorrelationId("timeout-123");
        assertEquals(2, elasticSearchResult.returnedHits());
        assertEquals("OK", elasticSearchResult.message());
        assertEquals("svc", elasticSearchResult.entries().get(0).serviceName());

        assertEquals(1, incidentEvidence.serviceMatches().size());
        assertEquals(1, incidentEvidence.problems().size());
        assertEquals(2, incidentEvidence.metrics().size());
        assertEquals("SERVICE-TIMEOUT", incidentEvidence.serviceMatches().get(0).entityId());
        assertEquals("P-26042756", incidentEvidence.problems().get(0).displayId());
        assertEquals("service.response.time.p95", incidentEvidence.metrics().get(0).metricLabel());

        assertEquals(1, fileCandidates.size());
        assertEquals("sample/runtime", fileCandidates.get(0).group());
        assertEquals("edge-client-service", fileCandidates.get(0).projectName());
        assertEquals("main", fileCandidates.get(0).branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", fileCandidates.get(0).filePath());
        assertEquals(95, fileCandidates.get(0).matchScore());

        var fileContent = gitLabAdapter.readFile(
                "sample/runtime",
                "edge-client-service",
                "main",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                4_000
        );

        assertEquals("sample/runtime", fileContent.group());
        assertEquals("edge-client-service", fileContent.projectName());
        assertEquals("main", fileContent.branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", fileContent.filePath());
        assertFalse(fileContent.truncated());

        var fileChunk = gitLabAdapter.readFileChunk(
                "sample/runtime",
                "edge-client-service",
                "main",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        );

        assertEquals(5, fileChunk.returnedStartLine());
        assertEquals(12, fileChunk.returnedEndLine());
        assertEquals(14, fileChunk.totalLines());
        assertTrue(fileChunk.content().contains("timeout(Duration.ofSeconds(2))"));
        assertFalse(fileChunk.truncated());
    }

}

