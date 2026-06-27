package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisRunHistoryController.class)
class AnalysisRunHistoryControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T10:05:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:06:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisRunHistoryService analysisRunHistoryService;

    @Test
    void shouldListRuns() throws Exception {
        when(analysisRunHistoryService.listRuns()).thenReturn(new LocalAnalysisRunListResponse(List.of(
                listItem("analysis-1", "incident-analysis", "corr-123")
        )));

        mockMvc.perform(get("/analysis/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.runs[0].feature").value("incident-analysis"))
                .andExpect(jsonPath("$.runs[0].name").value("corr-123"))
                .andExpect(jsonPath("$.runs[0].createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.runs[0].updatedAt").value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.runs[0].completedAt").value(COMPLETED_AT.toString()))
                .andExpect(jsonPath("$.runs[0].runPath").doesNotExist())
                .andExpect(jsonPath("$.runs[0].exportEnvelope").doesNotExist());

        verify(analysisRunHistoryService).listRuns();
    }

    @Test
    void shouldReturnRunDetail() throws Exception {
        when(analysisRunHistoryService.getRun("analysis-1")).thenReturn(detail("analysis-1", "corr-123", true));

        mockMvc.perform(get("/analysis/runs/analysis-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.feature").value("incident-analysis"))
                .andExpect(jsonPath("$.name").value("corr-123"))
                .andExpect(jsonPath("$.exportEnvelope.schema").value("tdw.analysis-export"))
                .andExpect(jsonPath("$.exportEnvelope.version").value(6))
                .andExpect(jsonPath("$.exportEnvelope.payload.job.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.continuationEnabled").value(true))
                .andExpect(jsonPath("$.runPath").doesNotExist())
                .andExpect(jsonPath("$.gitLabGroup").doesNotExist())
                .andExpect(jsonPath("$.authMode").doesNotExist())
                .andExpect(jsonPath("$.authPrincipalRef").doesNotExist());

        verify(analysisRunHistoryService).getRun("analysis-1");
    }

    @Test
    void shouldExportRunEnvelopeOnly() throws Exception {
        when(analysisRunHistoryService.exportRun("analysis-1")).thenReturn(exportEnvelope("analysis-1"));

        mockMvc.perform(get("/analysis/runs/analysis-1/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("tdw.analysis-export"))
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.payload.job.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.exportEnvelope").doesNotExist())
                .andExpect(jsonPath("$.continuation").doesNotExist())
                .andExpect(jsonPath("$.continuationEnabled").doesNotExist())
                .andExpect(jsonPath("$.copilotSessionId").doesNotExist())
                .andExpect(jsonPath("$.authMode").doesNotExist())
                .andExpect(jsonPath("$.authPrincipalRef").doesNotExist());

        verify(analysisRunHistoryService).exportRun("analysis-1");
    }

    @Test
    void shouldRenameRun() throws Exception {
        when(analysisRunHistoryService.renameRun("analysis-1", " Awaria koszyka w dev3 "))
                .thenReturn(detail("analysis-1", "Awaria koszyka w dev3", true));

        mockMvc.perform(patch("/analysis/runs/analysis-1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " Awaria koszyka w dev3 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.name").value("Awaria koszyka w dev3"));

        verify(analysisRunHistoryService).renameRun("analysis-1", " Awaria koszyka w dev3 ");
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(patch("/analysis/runs/analysis-1/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

        verifyNoInteractions(analysisRunHistoryService);
    }

    @Test
    void shouldSendChatMessageToLocalRun() throws Exception {
        when(analysisRunHistoryService.sendChatMessage(
                "analysis-1",
                new LocalAnalysisRunChatMessageRequest("Dopytaj o repo.")
        )).thenReturn(detail("analysis-1", "corr-123", true));

        mockMvc.perform(post("/analysis/runs/analysis-1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": " Dopytaj o repo. "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.continuationEnabled").value(true));

        verify(analysisRunHistoryService).sendChatMessage(
                "analysis-1",
                new LocalAnalysisRunChatMessageRequest("Dopytaj o repo.")
        );
    }

    @Test
    void shouldRejectBlankChatMessage() throws Exception {
        mockMvc.perform(post("/analysis/runs/analysis-1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("message"));

        verifyNoInteractions(analysisRunHistoryService);
    }

    @Test
    void shouldMapLocalChatFailureToServiceUnavailable() throws Exception {
        when(analysisRunHistoryService.sendChatMessage(
                "analysis-1",
                new LocalAnalysisRunChatMessageRequest("Dopytaj")
        )).thenThrow(new LocalAnalysisRunChatFailedException("Copilot unavailable."));

        mockMvc.perform(post("/analysis/runs/analysis-1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Dopytaj"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOCAL_ANALYSIS_RUN_CHAT_FAILED"));
    }

    @Test
    void shouldMapMissingRunToNotFound() throws Exception {
        when(analysisRunHistoryService.getRun("missing"))
                .thenThrow(new LocalAnalysisRunNotFoundException("missing"));

        mockMvc.perform(get("/analysis/runs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCAL_ANALYSIS_RUN_NOT_FOUND"));
    }

    @Test
    void shouldMapCorruptedRunToConflict() throws Exception {
        when(analysisRunHistoryService.getRun("analysis-1"))
                .thenThrow(new LocalAnalysisRunCorruptedException("analysis-1"));

        mockMvc.perform(get("/analysis/runs/analysis-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCAL_ANALYSIS_RUN_CORRUPTED"));
    }

    @Test
    void shouldDeleteRun() throws Exception {
        mockMvc.perform(delete("/analysis/runs/analysis-1"))
                .andExpect(status().isNoContent());

        verify(analysisRunHistoryService).deleteRun("analysis-1");
    }

    private LocalAnalysisRunListItemResponse listItem(String analysisId, String feature, String name) {
        return new LocalAnalysisRunListItemResponse(
                analysisId,
                feature,
                name,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT
        );
    }

    private LocalAnalysisRunDetailResponse detail(String analysisId, String name, boolean continuationEnabled) {
        return new LocalAnalysisRunDetailResponse(
                analysisId,
                "incident-analysis",
                name,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT,
                exportEnvelope(analysisId),
                continuationEnabled
        );
    }

    private com.fasterxml.jackson.databind.JsonNode exportEnvelope(String analysisId) {
        var envelope = objectMapper.createObjectNode();
        envelope.put("schema", "tdw.analysis-export");
        envelope.put("version", 6);
        envelope.put("exportedAt", COMPLETED_AT.toString());
        var payload = envelope.putObject("payload");
        payload.put("type", "analysis-job");
        var job = payload.putObject("job");
        job.put("analysisId", analysisId);
        job.put("correlationId", "corr-123");
        job.put("status", "COMPLETED");
        return envelope;
    }
}
