package pl.mkn.tdw.features.configdriftviewer.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.configdriftviewer.job.error.ConfigDriftViewerImportException;
import pl.mkn.tdw.features.configdriftviewer.job.importing
        .ConfigDriftViewerImportService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigDriftViewerImportController.class)
class ConfigDriftViewerImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigDriftViewerImportService importService;

    @Test
    void shouldReturnReadOnlyImportedSnapshot() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenReturn(importedSnapshot());

        mockMvc.perform(post("/api/config-drift-viewer/v1/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "tdw.config-drift-viewer-export",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("imported-job"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.imported").value(true));
    }

    @Test
    void shouldExposeSafeUnsupportedExportError() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenThrow(
                new ConfigDriftViewerImportException(
                        "Unsupported Config Drift Viewer export version."
                )
        );

        mockMvc.perform(post("/api/config-drift-viewer/v1/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID"
                ))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported Config Drift Viewer export version."
                ));
    }

    private ConfigDriftViewerJobStateSnapshot importedSnapshot() {
        var now = Instant.parse("2026-07-30T08:00:00Z");
        return new ConfigDriftViewerJobStateSnapshot(
                "imported-job",
                ConfigDriftViewerMode.BASIC,
                "runtime-config",
                List.of("crm-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null,
                "COMPLETED",
                null,
                null,
                null,
                null,
                now,
                now,
                now,
                List.of(),
                List.of(new ConfigDriftViewerComponentRunSnapshot(
                        "imported-job:0",
                        "crm-backend",
                        "CRM backend",
                        "backend",
                        "COMPLETED",
                        null,
                        null,
                        null,
                        null,
                        now,
                        now,
                        now,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null
                )),
                true
        );
    }
}
