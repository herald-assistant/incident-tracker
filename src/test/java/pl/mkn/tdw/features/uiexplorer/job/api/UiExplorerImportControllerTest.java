package pl.mkn.tdw.features.uiexplorer.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerImportException;
import pl.mkn.tdw.features.uiexplorer.job.importing.UiExplorerImportService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiExplorerImportController.class)
class UiExplorerImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UiExplorerImportService importService;

    @Test
    void shouldReturnReadOnlyImportedCrmSnapshot() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenReturn(importedSnapshot());

        mockMvc.perform(post("/api/ui-explorer/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "tdw.ui-explorer-export",
                                  "version": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("ui-explorer-import-crm-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exportAvailable").value(true))
                .andExpect(jsonPath("$.preparedPrompt").doesNotExist());
    }

    @Test
    void shouldExposeExactVersionImportError() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenThrow(
                new UiExplorerImportException("Unsupported UI Explorer export version.")
        );

        mockMvc.perform(post("/api/ui-explorer/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"tdw.ui-explorer-export\",\"version\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UI_EXPLORER_IMPORT_INVALID"))
                .andExpect(jsonPath("$.message").value("Unsupported UI Explorer export version."));
    }

    private UiExplorerJobStateSnapshot importedSnapshot() {
        var now = Instant.parse("2026-08-15T08:00:00Z");
        return new UiExplorerJobStateSnapshot(
                "ui-explorer-import-crm-1",
                new UiExplorerJobRequestSnapshot(
                        "crm-agent-portal",
                        "CRM Agent Portal",
                        "main",
                        "crm-contact-preferences",
                        "crm-commit-abc123",
                        List.of(),
                        "Document a strongly anonymized CRM scenario.",
                        "gpt-5.4",
                        "medium"
                ),
                UiExplorerJobStatus.COMPLETED,
                null,
                null,
                null,
                null,
                now.minusSeconds(30),
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                new UiExplorerSourceRevision("main", "crm-commit-abc123"),
                new UiExplorerOutputAvailability(
                        UiExplorerOutputAvailabilityStatus.AVAILABLE,
                        "UI_EXPLORER_IMPORTED_OUTPUT_AVAILABLE",
                        "A read-only imported CRM result is available.",
                        List.of()
                ),
                true
        );
    }
}
