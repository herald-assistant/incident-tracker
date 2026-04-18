package pl.mkn.incidenttracker.analysis.adapter.elasticsearch.mcp;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticMcpToolsTest {

    @Test
    void shouldSearchLogsThroughTool() {
        var elasticMcpTools = new ElasticMcpTools(new TestElasticLogPort());

        var response = elasticMcpTools.searchLogsByCorrelationId("timeout-123");

        assertEquals("timeout-123", response.correlationId());
        assertEquals(2, response.requestedSize());
        assertEquals(2, response.returnedHits());
        assertEquals("svc", response.entries().get(0).serviceName());
    }

    @Test
    void shouldReturnStructuredDatabaseLockLogsThroughTool() {
        var elasticMcpTools = new ElasticMcpTools(new TestElasticLogPort());

        var response = elasticMcpTools.searchLogsByCorrelationId("db-lock-123");

        assertEquals(2, response.requestedSize());
        assertEquals(2, response.returnedHits());
        assertTrue(response.entries().get(0).level().matches("ERROR|WARN"));
    }

}

