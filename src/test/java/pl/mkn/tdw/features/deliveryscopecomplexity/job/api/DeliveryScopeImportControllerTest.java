package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.error.DeliveryScopeImportException;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.importing.DeliveryScopeImportService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryScopeImportController.class)
class DeliveryScopeImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryScopeImportService importService;

    @Test
    void shouldReturnImportedAssessmentSnapshot() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenReturn(importedSnapshot());

        mockMvc.perform(post("/api/delivery-scope-complexity/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "tdw.delivery-scope-complexity-export",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("delivery-scope-import-1"))
                .andExpect(jsonPath("$.jiraProject").value("CRM"))
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_WARNINGS"));
    }

    @Test
    void shouldExposeExactImportValidationError() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenThrow(
                new DeliveryScopeImportException(
                        "Unsupported Delivery Scope Complexity export version."
                )
        );

        mockMvc.perform(post("/api/delivery-scope-complexity/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"tdw.delivery-scope-complexity-export\",\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_SCOPE_IMPORT_INVALID"))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported Delivery Scope Complexity export version."
                ));
    }

    private DeliveryScopeComplexityJobStateSnapshot importedSnapshot() {
        var now = Instant.parse("2026-08-17T10:00:00Z");
        return new DeliveryScopeComplexityJobStateSnapshot(
                "delivery-scope-import-1",
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
                new DeliveryScopeAggregateResponse(
                        0.0,
                        0.0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        "LOW",
                        null
                )
        );
    }
}
