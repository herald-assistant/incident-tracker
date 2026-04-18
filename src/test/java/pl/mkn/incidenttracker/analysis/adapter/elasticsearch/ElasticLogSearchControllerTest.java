package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
                                "tenant-alpha-main-dev1",
                                "backend-846b75885c-4v4gp",
                                "backend",
                                null,
                                ".ds-projects.TENANT-ALPHA.prj000000104201-2026.03.27-000377",
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

}

