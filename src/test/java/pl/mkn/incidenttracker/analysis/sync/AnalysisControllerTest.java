package pl.mkn.incidenttracker.analysis.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.analysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.flow.AnalysisResultResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void shouldReturnAnalysisResultForValidRequest() throws Exception {
        when(analysisService.analyze(any(AnalysisRequest.class)))
                .thenReturn(new AnalysisResultResponse(
                        "COMPLETED",
                        "timeout-123",
                        "dev3",
                        "dev/atlas",
                        "Synthetic analysis detected a probable timeout in downstream communication.",
                        "DOWNSTREAM_TIMEOUT",
                        "Check downstream latency, timeout configuration, and retry policy.",
                        "Timeout evidence is consistent across logs and runtime signals.",
                        "Dotknięta funkcja to pobranie danych downstream potrzebnych do złożenia odpowiedzi biznesowej.",
                        "Prompt body for timeout-123"
                ));

        mockMvc.perform(post("/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "timeout-123"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.correlationId").value("timeout-123"))
                .andExpect(jsonPath("$.environment").value("dev3"))
                .andExpect(jsonPath("$.gitLabBranch").value("dev/atlas"))
                .andExpect(jsonPath("$.summary").value("Synthetic analysis detected a probable timeout in downstream communication."))
                .andExpect(jsonPath("$.detectedProblem").value("DOWNSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.recommendedAction").value("Check downstream latency, timeout configuration, and retry policy."))
                .andExpect(jsonPath("$.rationale").value("Timeout evidence is consistent across logs and runtime signals."))
                .andExpect(jsonPath("$.affectedFunction").value("Dotknięta funkcja to pobranie danych downstream potrzebnych do złożenia odpowiedzi biznesowej."))
                .andExpect(jsonPath("$.prompt").value("Prompt body for timeout-123"));

        verify(analysisService).analyze(new AnalysisRequest("timeout-123"));
    }

    @Test
    void shouldReturnBadRequestWhenCorrelationIdIsMissing() throws Exception {
        mockMvc.perform(post("/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("correlationId")))
                .andExpect(jsonPath("$.fieldErrors[*].message", hasItems("correlationId must not be blank")));

        verifyNoInteractions(analysisService);
    }

    @Test
    void shouldReturnBadRequestWhenCorrelationIdIsBlank() throws Exception {
        mockMvc.perform(post("/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("correlationId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("correlationId must not be blank"));

        verifyNoInteractions(analysisService);
    }

    @Test
    void shouldIgnoreUnknownFieldsAndValidateOnlyCorrelationId() throws Exception {
        when(analysisService.analyze(any(AnalysisRequest.class)))
                .thenReturn(new AnalysisResultResponse(
                        "COMPLETED",
                        "timeout-123",
                        "dev3",
                        "dev/atlas",
                        "Synthetic analysis detected a probable timeout in downstream communication.",
                        "DOWNSTREAM_TIMEOUT",
                        "Check downstream latency, timeout configuration, and retry policy.",
                        "Timeout evidence is consistent across logs and runtime signals.",
                        "Dotknięta funkcja to pobranie danych downstream potrzebnych do złożenia odpowiedzi biznesowej.",
                        "Prompt body for timeout-123"
                ));

        mockMvc.perform(post("/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "timeout-123",
                                  "branch": "legacy-main"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.gitLabBranch").value("dev/atlas"));

        verify(analysisService).analyze(new AnalysisRequest("timeout-123"));
    }

    @Test
    void shouldReturnNotFoundWhenDiagnosticDataIsMissing() throws Exception {
        when(analysisService.analyze(any(AnalysisRequest.class)))
                .thenThrow(new AnalysisDataNotFoundException("not-found"));

        mockMvc.perform(post("/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "not-found"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_DATA_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No diagnostic data found for correlationId: not-found"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(analysisService).analyze(new AnalysisRequest("not-found"));
    }

}

