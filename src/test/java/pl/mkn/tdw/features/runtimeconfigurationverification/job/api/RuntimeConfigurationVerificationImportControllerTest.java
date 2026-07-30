package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationImportException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.importing
        .RuntimeConfigurationVerificationImportService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationVerificationImportController.class)
class RuntimeConfigurationVerificationImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationVerificationImportService importService;

    @Test
    void shouldReturnReadOnlyImportedSnapshot() throws Exception {
        when(importService.importReadOnly(any(JsonNode.class))).thenReturn(importedSnapshot());

        mockMvc.perform(post("/api/runtime-configuration-verification/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "tdw.runtime-configuration-verification-export",
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
                new RuntimeConfigurationVerificationImportException(
                        "Unsupported Runtime Configuration Verification export version."
                )
        );

        mockMvc.perform(post("/api/runtime-configuration-verification/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID"
                ))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported Runtime Configuration Verification export version."
                ));
    }

    private RuntimeConfigurationVerificationJobStateSnapshot importedSnapshot() {
        var now = Instant.parse("2026-07-30T08:00:00Z");
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                "imported-job",
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "clp-backend",
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
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                true
        );
    }
}
