package pl.mkn.incidenttracker.analysis.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.analysis.AnalysisConfidence;
import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisProblemNature;
import pl.mkn.incidenttracker.analysis.AnalysisVariantStatus;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.flow.AnalysisResultResponse;
import pl.mkn.incidenttracker.analysis.flow.AnalysisResultVariants;
import pl.mkn.incidenttracker.analysis.flow.AnalysisVariantResultResponse;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisJobController.class)
class AnalysisJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisJobService analysisJobService;

    @Test
    void shouldStartAnalysisJob() throws Exception {
        when(analysisJobService.startAnalysis(any(AnalysisRequest.class)))
                .thenReturn(new AnalysisJobResponse(
                        "job-123",
                        "timeout-123",
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
                                        null,
                                        null,
                                        List.of(),
                                        List.of(new AnalysisEvidenceReference("elasticsearch", "logs"))
                                )
                        ),
                        List.of(),
                        null
                ));

        mockMvc.perform(post("/analysis/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "timeout-123"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("job-123"))
                .andExpect(jsonPath("$.correlationId").value("timeout-123"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(analysisJobService).startAnalysis(new AnalysisRequest("timeout-123"));
    }

    @Test
    void shouldReturnAnalysisJobSnapshot() throws Exception {
        when(analysisJobService.getAnalysis("job-123"))
                .thenReturn(new AnalysisJobResponse(
                        "job-123",
                        "timeout-123",
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
                        new AnalysisResultResponse(
                                "COMPLETED",
                                "timeout-123",
                                "dev3",
                                "dev/atlas",
                                completedVariants()
                        )
                ));

        mockMvc.perform(get("/analysis/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.variants.conservative.detectedProblem").value("DOWNSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.result.variants.conservative.rationale").value("Timeout signals align across logs and runtime evidence."))
                .andExpect(jsonPath("$.result.variants.conservative.prompt").value("Prompt body for timeout-123"));

        verify(analysisJobService).getAnalysis("job-123");
    }

    @Test
    void shouldReturnNotFoundWhenJobIsMissing() throws Exception {
        when(analysisJobService.getAnalysis("missing-job"))
                .thenThrow(new AnalysisJobNotFoundException("missing-job"));

        mockMvc.perform(get("/analysis/jobs/missing-job"))
                .andExpect(status().isNotFound());

        verify(analysisJobService).getAnalysis("missing-job");
    }

    private static AnalysisResultVariants completedVariants() {
        return new AnalysisResultVariants(
                new AnalysisVariantResultResponse(
                        AnalysisMode.CONSERVATIVE,
                        AnalysisVariantStatus.COMPLETED,
                        "DOWNSTREAM_TIMEOUT",
                        "Structured evidence points to a downstream timeout in the catalog-service call chain.",
                        "Inspect recent HTTP client timeout changes first.",
                        "Timeout signals align across logs and runtime evidence.",
                        AnalysisProblemNature.CONFIRMED,
                        AnalysisConfidence.HIGH,
                        "Prompt body for timeout-123",
                        null
                ),
                new AnalysisVariantResultResponse(
                        AnalysisMode.EXPLORATORY,
                        AnalysisVariantStatus.DISABLED,
                        "",
                        "",
                        "",
                        "",
                        AnalysisProblemNature.HYPOTHESIS,
                        null,
                        null,
                        null
                )
        );
    }

}

