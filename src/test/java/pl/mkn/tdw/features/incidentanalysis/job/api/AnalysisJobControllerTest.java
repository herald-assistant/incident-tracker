package pl.mkn.tdw.features.incidentanalysis.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.tdw.features.incidentanalysis.job.AnalysisJobFacade;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobInputException;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobNotFoundException;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisJobController.class)
class AnalysisJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisJobFacade analysisJobFacade;

    @Test
    void shouldStartAnalysisJob() throws Exception {
        when(analysisJobFacade.startAnalysis(any(AnalysisJobStartRequest.class)))
                .thenReturn(new AnalysisJobStateSnapshot(
                        "job-123",
                        "timeout-123",
                        "gpt-5.4",
                        "high",
                        "QUEUED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-12T18:00:00Z"),
                        Instant.parse("2026-04-12T18:00:00Z"),
                        null,
                        List.of(
                                new AnalysisJobStepResponse(
                                        "ELASTICSEARCH_LOGS",
                                        "Zbieranie logow z Elasticsearch",
                                        "SOURCE",
                                        "PENDING",
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(),
                                        List.of(new AnalysisEvidenceReference("elasticsearch", "logs"))
                                )
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ));

        mockMvc.perform(multipart("/api/analysis/jobs")
                        .param("source", "ELASTICSEARCH")
                        .param("correlationId", "timeout-123")
                        .param("model", "gpt-5.4")
                        .param("reasoningEffort", "high"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("job-123"))
                .andExpect(jsonPath("$.correlationId").value("timeout-123"))
                .andExpect(jsonPath("$.aiModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.reasoningEffort").value("high"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(analysisJobFacade).startAnalysis(new AnalysisJobStartRequest("timeout-123", "gpt-5.4", "high"));
    }

    @Test
    void shouldRejectMissingLogSource() throws Exception {
        mockMvc.perform(multipart("/api/analysis/jobs")
                        .param("correlationId", "timeout-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCIDENT_LOG_SOURCE_REQUIRED"));
    }

    @Test
    void shouldReturnBadRequestWhenCsvFileIsMissing() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                null,
                "INCIDENT_LOG_FILE_MISSING"
        );
    }

    @Test
    void shouldReturnBadRequestWhenCsvIsInvalid() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                csvFile("not,a,valid,incident,csv"),
                "INCIDENT_LOG_FILE_INVALID_CSV"
        );
    }

    @Test
    void shouldReturnBadRequestWhenCsvColumnsAreMissing() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                csvFile("""
                        "@timestamp","fields.correlationId"
                        "2026-04-11T20:57:33.285Z","csv-timeout-123"
                        """),
                "INCIDENT_LOG_FILE_MISSING_COLUMNS"
        );
    }

    @Test
    void shouldReturnBadRequestWhenCsvIsEmpty() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                csvFile("""
                        "@timestamp","fields.correlationId","fields.type","fields.microservice","fields.class","fields.message","fields.exception","fields.thread","fields.spanId","kubernetes.namespace","kubernetes.pod.name","kubernetes.container.name","container.image.name"
                        """),
                "INCIDENT_LOG_FILE_EMPTY"
        );
    }

    @Test
    void shouldReturnBadRequestWhenCsvHasMultipleCorrelationIds() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                csvFile("multi-correlation-csv"),
                "INCIDENT_LOG_FILE_MULTIPLE_CORRELATION_IDS"
        );
    }

    @Test
    void shouldReturnBadRequestWhenCsvTimestampIsInvalid() throws Exception {
        assertStartRejectedByService(
                "CSV_UPLOAD",
                null,
                csvFile("invalid-timestamp-csv"),
                "INCIDENT_LOG_FILE_INVALID_TIMESTAMP"
        );
    }

    @Test
    void shouldReturnBadRequestWhenElasticsearchStartIsDisabled() throws Exception {
        assertStartRejectedByService(
                "ELASTICSEARCH",
                "timeout-123",
                null,
                "ELASTICSEARCH_LOG_SOURCE_NOT_CONFIGURED"
        );
    }

    @Test
    void shouldExposeInputOptions() throws Exception {
        when(analysisJobFacade.inputOptions())
                .thenReturn(new AnalysisJobInputOptionsResponse(
                        new AnalysisJobInputOptionsResponse.LogSourceOption(
                                "ELASTICSEARCH",
                                false,
                                "Start po correlationId jest zablokowany."
                        ),
                        new AnalysisJobInputOptionsResponse.LogSourceOption(
                                "CSV_UPLOAD",
                                true,
                                null
                        )
                ));

        mockMvc.perform(get("/api/analysis/jobs/input-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elasticsearch.source").value("ELASTICSEARCH"))
                .andExpect(jsonPath("$.elasticsearch.enabled").value(false))
                .andExpect(jsonPath("$.csvUpload.source").value("CSV_UPLOAD"))
                .andExpect(jsonPath("$.csvUpload.enabled").value(true));

        verify(analysisJobFacade).inputOptions();
    }

    @Test
    void shouldForwardCsvUploadStartRequestToService() throws Exception {
        when(analysisJobFacade.startAnalysis(any(AnalysisJobStartRequest.class)))
                .thenReturn(new AnalysisJobStateSnapshot(
                        "job-csv",
                        "csv-derived-later",
                        null,
                        null,
                        "QUEUED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-12T18:00:00Z"),
                        Instant.parse("2026-04-12T18:00:00Z"),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ));

        var csvFile = new MockMultipartFile(
                "logFile",
                "logs.csv",
                "text/csv",
                "@timestamp,fields.correlationId\nJul 4, 2026 @ 10:57:36.853,corr-1\n".getBytes()
        );

        mockMvc.perform(multipart("/api/analysis/jobs")
                        .file(csvFile)
                        .param("source", "CSV_UPLOAD"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("job-csv"));

        verify(analysisJobFacade).startAnalysis(new AnalysisJobStartRequest(
                AnalysisJobLogSource.CSV_UPLOAD,
                null,
                csvFile,
                null,
                null
        ));
    }

    @Test
    void shouldReturnAnalysisJobSnapshot() throws Exception {
        when(analysisJobFacade.getAnalysis("job-123"))
                .thenReturn(new AnalysisJobStateSnapshot(
                        "job-123",
                        "timeout-123",
                        "gpt-5.4",
                        "medium",
                        "COMPLETED",
                        null,
                        null,
                        "dev3",
                        "dev/atlas",
                        null,
                        null,
                        Instant.parse("2026-04-12T18:00:00Z"),
                        Instant.parse("2026-04-12T18:01:00Z"),
                        Instant.parse("2026-04-12T18:01:00Z"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "Prompt body for timeout-123",
                        new AnalysisResultResponse(
                                "COMPLETED",
                                "timeout-123",
                                "dev3",
                                "dev/atlas",
                                "DOWNSTREAM_TIMEOUT",
                                "Profil klienta CRM",
                                "CRM Customer Context",
                                "CRM Customer Team",
                                "Analiza funkcjonalna opisuje proces rozliczenia klienta i miejsce pobrania danych katalogowych.",
                                "Analiza techniczna wskazuje klienta HTTP, timeout i rekomendowana poprawke.",
                                "medium",
                                List.of("Brak potwierdzenia po stronie downstream."),
                                "Prompt body for timeout-123"
                        )
                ));

        mockMvc.perform(get("/api/analysis/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.preparedPrompt").value("Prompt body for timeout-123"))
                .andExpect(jsonPath("$.result.detectedProblem").value("DOWNSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.result.affectedProcess").value("Profil klienta CRM"))
                .andExpect(jsonPath("$.result.affectedBoundedContext").value("CRM Customer Context"))
                .andExpect(jsonPath("$.result.affectedTeam").value("CRM Customer Team"))
                .andExpect(jsonPath("$.result.functionalAnalysis").value("Analiza funkcjonalna opisuje proces rozliczenia klienta i miejsce pobrania danych katalogowych."))
                .andExpect(jsonPath("$.result.technicalAnalysis").value("Analiza techniczna wskazuje klienta HTTP, timeout i rekomendowana poprawke."))
                .andExpect(jsonPath("$.result.confidence").value("medium"))
                .andExpect(jsonPath("$.result.visibilityLimits[0]").value("Brak potwierdzenia po stronie downstream."))
                .andExpect(jsonPath("$.result.prompt").value("Prompt body for timeout-123"));

        verify(analysisJobFacade).getAnalysis("job-123");
    }

    @Test
    void shouldStartFollowUpChatMessage() throws Exception {
        when(analysisJobFacade.startChatMessage(any(String.class), any(AnalysisChatMessageRequest.class)))
                .thenReturn(new AnalysisJobStateSnapshot(
                        "job-123",
                        "timeout-123",
                        "gpt-5.4",
                        "medium",
                        "COMPLETED",
                        null,
                        null,
                        "dev3",
                        "dev/atlas",
                        null,
                        null,
                        Instant.parse("2026-04-12T18:00:00Z"),
                        Instant.parse("2026-04-12T18:02:00Z"),
                        Instant.parse("2026-04-12T18:01:00Z"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new AnalysisChatMessageResponse(
                                "msg-1",
                                "USER",
                                "COMPLETED",
                                "Sprawdz jeszcze repo.",
                                null,
                                null,
                                Instant.parse("2026-04-12T18:02:00Z"),
                                Instant.parse("2026-04-12T18:02:00Z"),
                                Instant.parse("2026-04-12T18:02:00Z"),
                                List.of(),
                                List.of(),
                                List.of(),
                                null
                        )),
                        "Prompt body for timeout-123",
                        null
                ));

        mockMvc.perform(post("/api/analysis/jobs/job-123/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Sprawdz jeszcze repo."
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("job-123"))
                .andExpect(jsonPath("$.chatMessages", hasSize(1)))
                .andExpect(jsonPath("$.chatMessages[0].role").value("USER"));

        verify(analysisJobFacade).startChatMessage(
                "job-123",
                new AnalysisChatMessageRequest("Sprawdz jeszcze repo.")
        );
    }

    @Test
    void shouldReturnNotFoundWhenJobIsMissing() throws Exception {
        when(analysisJobFacade.getAnalysis("missing-job"))
                .thenThrow(new AnalysisJobNotFoundException("missing-job"));

        mockMvc.perform(get("/api/analysis/jobs/missing-job"))
                .andExpect(status().isNotFound());

        verify(analysisJobFacade).getAnalysis("missing-job");
    }

    @Test
    void shouldKeepLegacyAnalysisJobsRoute() throws Exception {
        when(analysisJobFacade.startAnalysis(any(AnalysisJobStartRequest.class)))
                .thenReturn(new AnalysisJobStateSnapshot(
                        "job-legacy",
                        "timeout-legacy",
                        null,
                        null,
                        "QUEUED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-04-12T18:00:00Z"),
                        Instant.parse("2026-04-12T18:00:00Z"),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ));

        mockMvc.perform(multipart("/analysis/jobs")
                        .param("source", "ELASTICSEARCH")
                        .param("correlationId", "timeout-legacy"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("job-legacy"));

        verify(analysisJobFacade).startAnalysis(new AnalysisJobStartRequest("timeout-legacy", null, null));
    }

    private void assertStartRejectedByService(
            String source,
            String correlationId,
            MockMultipartFile logFile,
            String code
    ) throws Exception {
        when(analysisJobFacade.startAnalysis(any(AnalysisJobStartRequest.class)))
                .thenThrow(new AnalysisJobInputException(code, "Rejected input: " + code));

        MockMultipartHttpServletRequestBuilder request = multipart("/api/analysis/jobs");
        if (logFile != null) {
            request.file(logFile);
        }
        request.param("source", source);
        if (correlationId != null) {
            request.param("correlationId", correlationId);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Rejected input: " + code))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));

        verify(analysisJobFacade).startAnalysis(any(AnalysisJobStartRequest.class));
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "logFile",
                "logs.csv",
                "text/csv",
                content.getBytes()
        );
    }

}


