package pl.mkn.tdw.agenttools.elasticsearch.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallLogsRequest;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallLogsResult;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSample;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSummaryRequest;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSummaryResult;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpStatusBucket;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogDetailLevel;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogEntry;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchService;
import pl.mkn.tdw.integrations.elasticsearch.TestElasticLogPort;

import java.util.LinkedHashMap;
import java.util.List;

import pl.mkn.tdw.testsupport.agenttools.ElasticMcpToolsTestCreator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticMcpToolsTest {

    @Test
    void shouldSearchLogsThroughTool() {
        var elasticMcpTools = ElasticMcpToolsTestCreator.create(new TestElasticLogPort());

        var response = elasticMcpTools.searchLogsByCorrelationId("timeout-123");

        assertEquals("timeout-123", response.correlationId());
        assertEquals(2, response.requestedSize());
        assertEquals(2, response.returnedHits());
        assertEquals("svc", response.entries().get(0).serviceName());
    }

    @Test
    void shouldReturnStructuredDatabaseLockLogsThroughTool() {
        var elasticMcpTools = ElasticMcpToolsTestCreator.create(new TestElasticLogPort());

        var response = elasticMcpTools.searchLogsByCorrelationId("db-lock-123");

        assertEquals(2, response.requestedSize());
        assertEquals(2, response.returnedHits());
        assertTrue(response.entries().get(0).level().matches("ERROR|WARN"));
    }

    @Test
    void shouldSummarizeHttpCallsByPathThroughTool() {
        var service = mock(ElasticLogSearchService.class);
        when(service.summarizeHttpCalls(any(ElasticHttpCallSummaryRequest.class)))
                .thenReturn(new ElasticHttpCallSummaryResult(
                        "/external/path/",
                        "GET",
                        "backend",
                        7,
                        "logs-*",
                        300,
                        3,
                        2,
                        12,
                        false,
                        List.of(new ElasticHttpStatusBucket("200", 1), new ElasticHttpStatusBucket("500", 1)),
                        List.of(new ElasticHttpCallSample(
                                "2026-05-12T10:15:00Z",
                                "GET",
                                "/external/path/125",
                                200,
                                "corr-ok",
                                "backend",
                                "ExternalClient",
                                "GET /external/path/125 status=200",
                                "logs-2026.05.12",
                                "doc-1"
                        )),
                        "OK"
                ));
        var elasticMcpTools = ElasticMcpToolsTestCreator.create(new TestElasticLogPort(), service);

        var response = elasticMcpTools.summarizeHttpCallsByPath(
                " /external/path/ ",
                "GET",
                "backend",
                7,
                300,
                "Porownuje wywolania tego samego endpointu.",
                elasticToolContext()
        );

        assertEquals("/external/path/", response.pathPattern());
        assertEquals(2, response.statusBuckets().size());
        assertEquals("/external/path/125", response.samples().get(0).path());
        verify(service).summarizeHttpCalls(new ElasticHttpCallSummaryRequest(
                "/external/path/",
                "GET",
                "backend",
                7,
                300
        ));
    }

    @Test
    void shouldFetchHttpCallLogsByPathWithoutCurrentCorrelationIdFilter() {
        var service = mock(ElasticLogSearchService.class);
        when(service.fetchHttpCallLogs(any(ElasticHttpCallLogsRequest.class)))
                .thenReturn(new ElasticHttpCallLogsResult(
                        null,
                        "/external/path/125",
                        200,
                        "GET",
                        7,
                        ElasticLogDetailLevel.FULL,
                        "logs-*",
                        50,
                        1,
                        1,
                        9,
                        false,
                        List.of(logEntry("GET /external/path/125 status=200")),
                        "OK"
                ));
        var elasticMcpTools = ElasticMcpToolsTestCreator.create(new TestElasticLogPort(), service);

        var response = elasticMcpTools.fetchHttpCallLogs(
                "/external/path/125",
                200,
                "GET",
                7,
                50,
                "full",
                "Sprawdzam udane wywolanie porownawcze.",
                elasticToolContext()
        );

        assertEquals("/external/path/125", response.path());
        assertEquals(ElasticLogDetailLevel.FULL, response.detailLevel());
        assertEquals(1, response.entries().size());
        verify(service).fetchHttpCallLogs(new ElasticHttpCallLogsRequest(
                null,
                "/external/path/125",
                200,
                "GET",
                7,
                50,
                ElasticLogDetailLevel.FULL
        ));
    }

    @Test
    void shouldFetchHttpCallLogsForCurrentCorrelationIdWhenPathIsOmitted() {
        var service = mock(ElasticLogSearchService.class);
        when(service.fetchHttpCallLogs(any(ElasticHttpCallLogsRequest.class)))
                .thenReturn(new ElasticHttpCallLogsResult(
                        "corr-123",
                        null,
                        null,
                        null,
                        7,
                        ElasticLogDetailLevel.COMPACT,
                        "logs-*",
                        50,
                        1,
                        1,
                        9,
                        false,
                        List.of(logEntry("incident log")),
                        "OK"
                ));
        var elasticMcpTools = ElasticMcpToolsTestCreator.create(new TestElasticLogPort(), service);

        var response = elasticMcpTools.fetchHttpCallLogs(
                null,
                null,
                null,
                7,
                50,
                null,
                "Pobieram logi aktualnego incydentu.",
                elasticToolContext()
        );

        assertEquals("corr-123", response.correlationId());
        assertEquals(ElasticLogDetailLevel.COMPACT, response.detailLevel());
        verify(service).fetchHttpCallLogs(new ElasticHttpCallLogsRequest(
                "corr-123",
                null,
                null,
                null,
                7,
                50,
                ElasticLogDetailLevel.COMPACT
        ));
    }

    private ToolContext elasticToolContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put(AgentToolContextKeys.CORRELATION_ID, "corr-123");
        context.put(AgentToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(AgentToolContextKeys.COPILOT_SESSION_ID, "session-1");
        context.put(AgentToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        return new ToolContext(context);
    }

    private ElasticLogEntry logEntry(String message) {
        return new ElasticLogEntry(
                "2026-05-12T10:15:00Z",
                "INFO",
                "backend",
                "ExternalClient",
                message,
                null,
                "main",
                null,
                "ns",
                "pod",
                "backend",
                "image",
                "logs-2026.05.12",
                "doc-log-1",
                false,
                false
        );
    }

}

