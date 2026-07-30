package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewService;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewException;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationWorkbenchPreviewController.class)
class RuntimeConfigurationWorkbenchPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationWorkbenchPreviewService previewService;

    @Test
    void shouldReturnNormalizedReadonlyPreview() throws Exception {
        var request = new RuntimeConfigurationWorkbenchPreviewRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                null
        );
        when(previewService.preview(request)).thenReturn(response());

        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": " runtime-config ",
                                  "systemId": " billing-api ",
                                  "sourceBranch": " dev1 ",
                                  "targetBranch": " zt001 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.repositoryId").value("runtime-config"))
                .andExpect(jsonPath("$.systemId").value("billing-api"))
                .andExpect(jsonPath("$.preparedPrompt").value("AI-safe prompt"))
                .andExpect(jsonPath("$.artifactContents['manifest.json']").value("{}"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("raw-secret")
                )));

        verify(previewService).preview(request);
    }

    @Test
    void shouldRejectInvalidBranchPair() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemId": "billing-api",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "dev1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnsafeCodeReference() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "DEEP",
                                  "repositoryId": "runtime-config",
                                  "systemId": "billing-api",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001",
                                  "codeRef": "release/../secret"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldExposeOnlyStableErrorWhenPreviewFails() throws Exception {
        var request = new RuntimeConfigurationWorkbenchPreviewRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                null
        );
        when(previewService.preview(request))
                .thenThrow(new RuntimeConfigurationWorkbenchPreviewException());

        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemId": "billing-api",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        "RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED"
                ))
                .andExpect(jsonPath("$.message").value(
                        "Runtime configuration preview did not complete. Check source coverage and retry."
                ))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("raw-source-secret")
                )));
    }

    private RuntimeConfigurationWorkbenchPreviewResponse response() {
        return new RuntimeConfigurationWorkbenchPreviewResponse(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                null,
                null,
                null,
                new RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationSummary(
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of()
                ),
                null,
                "AI-safe prompt",
                Map.of("manifest.json", "{}"),
                List.of(new RuntimeConfigurationWorkbenchPreviewResponse.ArtifactSummary(
                        "manifest.json",
                        2,
                        false
                )),
                List.of()
        );
    }
}
