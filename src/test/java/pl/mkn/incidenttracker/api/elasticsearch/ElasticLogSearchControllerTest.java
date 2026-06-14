package pl.mkn.incidenttracker.api.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallLogsRequest;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallLogsResult;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallSample;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallSummaryRequest;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallSummaryResult;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpStatusBucket;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogDetailLevel;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchRequest;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchResult;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ElasticLogSearchController.class)
class ElasticLogSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElasticLogSearchService elasticLogSearchService;

    @Test
    void shouldSearchLogsForValidRequest() throws Exception {
        when(elasticLogSearchService.search(any(ElasticLogSearchRequest.class)))
                .thenReturn(new ElasticLogSearchResult(
                        "69dab5bdc21dbf9099025d075e682e8a",
                        "logs-*",
                        200,
                        2,
                        2,
                        307,
                        false,
                        List.of(new ElasticLogEntry(
                                "2026-04-11T20:57:33.285Z",
                                "ERROR",
                                "case-evaluation-service",
                                "c.e.synthetic.workflow.WorkflowApiExceptionHandler",
                                "Loan processing exception",
                                "EntityNotFoundException",
                                "https-jsse-nio-8443-exec-10",
                                "a8cdba8fe9a5ec96",
                                "crm-main-dev1",
                                "backend-846b75885c-4v4gp",
                                "backend",
                                null,
                                ".ds-projects.CRM.prj000000104201-2026.03.27-000377",
                                "AZ1-Vg6o1WPiTTEp9_tC",
                                false,
                                false
                        )),
                        "OK"
                ));

        mockMvc.perform(post("/api/elasticsearch/logs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "69dab5bdc21dbf9099025d075e682e8a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("69dab5bdc21dbf9099025d075e682e8a"))
                .andExpect(jsonPath("$.indexPattern").value("logs-*"))
                .andExpect(jsonPath("$.returnedHits").value(2))
                .andExpect(jsonPath("$.entries[0].level").value("ERROR"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(elasticLogSearchService).search(new ElasticLogSearchRequest(
                "69dab5bdc21dbf9099025d075e682e8a"
        ));
    }

    @Test
    void shouldReturnBadRequestForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/elasticsearch/logs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());

        verifyNoInteractions(elasticLogSearchService);
    }

    @Test
    void shouldSummarizeHttpCallsForValidRequest() throws Exception {
        when(elasticLogSearchService.summarizeHttpCalls(any(ElasticHttpCallSummaryRequest.class)))
                .thenReturn(new ElasticHttpCallSummaryResult(
                        "/external/path/",
                        "GET",
                        "backend",
                        7,
                        "logs-*",
                        300,
                        12,
                        2,
                        20,
                        false,
                        List.of(new ElasticHttpStatusBucket("200", 1)),
                        List.of(new ElasticHttpCallSample(
                                "2026-05-11T10:00:00Z",
                                "GET",
                                "/external/path/122",
                                200,
                                "corr-ok",
                                "backend",
                                "HttpLogger",
                                "GET /external/path/122 status: 200",
                                "logs-2026",
                                "ok-1"
                        )),
                        "OK"
                ));

        mockMvc.perform(post("/api/elasticsearch/logs/http-calls/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pathPattern": "/external/path/",
                                  "method": "GET",
                                  "serviceName": "backend",
                                  "timeWindowDays": 7,
                                  "sampleSize": 300
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathPattern").value("/external/path/"))
                .andExpect(jsonPath("$.statusBuckets[0].status").value("200"))
                .andExpect(jsonPath("$.samples[0].correlationId").value("corr-ok"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(elasticLogSearchService).summarizeHttpCalls(new ElasticHttpCallSummaryRequest(
                "/external/path/",
                "GET",
                "backend",
                7,
                300
        ));
    }

    @Test
    void shouldFetchHttpCallLogsForValidRequest() throws Exception {
        when(elasticLogSearchService.fetchHttpCallLogs(any(ElasticHttpCallLogsRequest.class)))
                .thenReturn(new ElasticHttpCallLogsResult(
                        "corr-ok",
                        "/external/path/122",
                        200,
                        "GET",
                        7,
                        ElasticLogDetailLevel.COMPACT,
                        "logs-*",
                        50,
                        1,
                        1,
                        18,
                        false,
                        List.of(new ElasticLogEntry(
                                "2026-05-11T10:00:00Z",
                                "INFO",
                                "backend",
                                "HttpLogger",
                                "GET /external/path/122 status: 200",
                                "",
                                "http-nio-1",
                                "span-1",
                                "namespace",
                                "pod",
                                "backend",
                                null,
                                "logs-2026",
                                "ok-1",
                                false,
                                false
                        )),
                        "OK"
                ));

        mockMvc.perform(post("/api/elasticsearch/logs/http-calls/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-ok",
                                  "path": "/external/path/122",
                                  "status": 200,
                                  "method": "GET",
                                  "timeWindowDays": 7,
                                  "size": 50,
                                  "detailLevel": "COMPACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("corr-ok"))
                .andExpect(jsonPath("$.detailLevel").value("COMPACT"))
                .andExpect(jsonPath("$.entries[0].message").value("GET /external/path/122 status: 200"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(elasticLogSearchService).fetchHttpCallLogs(new ElasticHttpCallLogsRequest(
                "corr-ok",
                "/external/path/122",
                200,
                "GET",
                7,
                50,
                ElasticLogDetailLevel.COMPACT
        ));
    }

    @Test
    void shouldReturnBadRequestWhenHttpFetchHasNoCorrelationIdOrPath() throws Exception {
        mockMvc.perform(post("/api/elasticsearch/logs/http-calls/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "detailLevel": "COMPACT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
