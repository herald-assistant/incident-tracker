package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentImportException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.importing.DeliveryAssessmentImportService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryAssessmentImportController.class)
class DeliveryAssessmentImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryAssessmentImportService importService;

    @Test
    void shouldReturnImportedAssessmentSnapshot() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenReturn(importedSnapshot());

        mockMvc.perform(post("/api/delivery-effectiveness-assessment/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "tdw.delivery-effectiveness-assessment-export",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("delivery-assessment-import-1"))
                .andExpect(jsonPath("$.jiraProject").value("CRM"))
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_WARNINGS"));
    }

    @Test
    void shouldExposeExactImportValidationError() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenThrow(
                new DeliveryAssessmentImportException(
                        "Unsupported Delivery Effectiveness Assessment export version."
                )
        );

        mockMvc.perform(post("/api/delivery-effectiveness-assessment/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"tdw.delivery-effectiveness-assessment-export\",\"version\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_ASSESSMENT_IMPORT_INVALID"))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported Delivery Effectiveness Assessment export version."
                ));
    }

    private DeliveryEffectivenessAssessmentJobStateSnapshot importedSnapshot() {
        var now = Instant.parse("2026-08-17T10:00:00Z");
        return new DeliveryEffectivenessAssessmentJobStateSnapshot(
                "delivery-assessment-import-1",
                "CRM",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                "gpt-5.4-mini",
                "medium",
                "COMPLETED_WITH_WARNINGS",
                "COMPLETED",
                "Completed with warnings",
                null,
                null,
                now.minusSeconds(60),
                now,
                now,
                0,
                0,
                0,
                "project = CRM",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DeliveryAssessmentAggregateResponse(
                        0,
                        Map.of(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        "LOW",
                        null
                ),
                List.of(),
                null
        );
    }
}
