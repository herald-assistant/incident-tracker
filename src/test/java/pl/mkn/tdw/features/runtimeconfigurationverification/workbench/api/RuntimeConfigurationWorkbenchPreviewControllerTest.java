package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewException;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench
        .RuntimeConfigurationWorkbenchPreviewService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationWorkbenchPreviewController.class)
class RuntimeConfigurationWorkbenchPreviewControllerTest {

    private static final String PREVIEW_ID = "019fb000-1f4d-79e0-8de1-daae931197ac";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationWorkbenchPreviewService previewService;

    @Test
    void shouldReturnNormalizedCompactPreviewWithoutLegacyPayload() throws Exception {
        var request = new RuntimeConfigurationWorkbenchPreviewRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                null
        );
        when(previewService.preview(request)).thenReturn(response(false));

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
                .andExpect(jsonPath("$.previewId").value(PREVIEW_ID))
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.repositoryId").value("runtime-config"))
                .andExpect(jsonPath("$.systemId").value("billing-api"))
                .andExpect(jsonPath("$.counts.nodes").value(3))
                .andExpect(jsonPath("$.aiInputGenerated").value(false))
                .andExpect(jsonPath("$.artifacts").isEmpty())
                .andExpect(jsonPath("$.configurationDiff").doesNotExist())
                .andExpect(jsonPath("$.preparedPrompt").doesNotExist())
                .andExpect(jsonPath("$.artifactContents").doesNotExist())
                .andExpect(jsonPath("$.mapping").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("raw-secret")
                )));

        verify(previewService).preview(request);
    }

    @Test
    void shouldMarkAiInputAsGeneratedOnlyForDeepPreview() throws Exception {
        var request = new RuntimeConfigurationWorkbenchPreviewRequest(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                "release-42"
        );
        when(previewService.preview(request)).thenReturn(response(true));

        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "DEEP",
                                  "repositoryId": "runtime-config",
                                  "systemId": "billing-api",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001",
                                  "codeRef": "release-42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEEP"))
                .andExpect(jsonPath("$.aiInputGenerated").value(true))
                .andExpect(jsonPath("$.artifacts[0].name")
                        .value("runtime-configuration/configuration-tree.yaml"));
    }

    @Test
    void shouldServeOperatorProjectionLazily() throws Exception {
        var projection = new RuntimeConfigurationDiffProjection("dev1", "zt001", List.of());
        when(previewService.configurationDiff(PREVIEW_ID)).thenReturn(
                new RuntimeConfigurationWorkbenchConfigurationDiffResponse(
                        PREVIEW_ID,
                        projection
                )
        );

        mockMvc.perform(get(
                        "/api/runtime-configuration-verification/workbench/preview/{previewId}/configuration-diff",
                        PREVIEW_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(PREVIEW_ID))
                .andExpect(jsonPath("$.configurationDiff.sourceBranch").value("dev1"))
                .andExpect(jsonPath("$.configurationDiff.targetBranch").value("zt001"))
                .andExpect(jsonPath("$.configurationDiff.files").isEmpty());
    }

    @Test
    void shouldServeOnlyRequestedMappingPage() throws Exception {
        var page = new RuntimeConfigurationWorkbenchMappingPage(
                PREVIEW_ID,
                100,
                50,
                136,
                855,
                false,
                List.of(new RuntimeConfigurationWorkbenchMappingPage.Item(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        0,
                        2,
                        "timeout",
                        "service.timeout",
                        "property-1",
                        "service.property-1",
                        RuntimeConfigurationValueType.NUMBER,
                        RuntimeConfigurationValueType.NUMBER,
                        RuntimeConfigurationChangeKind.CHANGED,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        "value-1",
                        "value-2",
                        List.of("difference-1")
                ))
        );
        when(previewService.mapping(PREVIEW_ID, 100, 50, false)).thenReturn(page);

        mockMvc.perform(get(
                        "/api/runtime-configuration-verification/workbench/preview/{previewId}/mapping",
                        PREVIEW_ID
                )
                        .param("offset", "100")
                        .param("limit", "50")
                        .param("changedOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(100))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.totalItems").value(136))
                .andExpect(jsonPath("$.totalNodes").value(855))
                .andExpect(jsonPath("$.items[0].originalPath").value("service.timeout"))
                .andExpect(jsonPath("$.items[0].sanitizedPath").value("service.property-1"))
                .andExpect(jsonPath("$.items[0].differenceIds[0]").value("difference-1"));

        verify(previewService).mapping(PREVIEW_ID, 100, 50, false);
    }

    @Test
    void shouldReturnSafeNotFoundForMissingOrExpiredSnapshot() throws Exception {
        when(previewService.source(PREVIEW_ID))
                .thenThrow(new RuntimeConfigurationWorkbenchPreviewNotFoundException());

        mockMvc.perform(get(
                        "/api/runtime-configuration-verification/workbench/preview/{previewId}/source",
                        PREVIEW_ID
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "Runtime configuration preview is missing or expired. Run a new preview."
                ));
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
    void shouldRejectCodeReferenceInBasicMode() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/workbench/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemId": "billing-api",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001",
                                  "codeRef": "release-42"
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

    private RuntimeConfigurationWorkbenchPreviewResponse response(boolean deep) {
        return new RuntimeConfigurationWorkbenchPreviewResponse(
                PREVIEW_ID,
                Instant.parse("2026-07-30T10:10:00Z"),
                deep
                        ? RuntimeConfigurationVerificationMode.DEEP
                        : RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                deep ? "release-42" : null,
                new RuntimeConfigurationWorkbenchPreviewResponse.SourceSummary(
                        "backend",
                        true,
                        true,
                        true,
                        true
                ),
                new RuntimeConfigurationWorkbenchPreviewResponse.Counts(1, 3, 1, 1, 0),
                new RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationSummary(
                        deep ? 3 : 0,
                        deep ? 2 : 0,
                        deep ? 2 : 0,
                        deep ? 2 : 0,
                        0
                ),
                new RuntimeConfigurationWorkbenchPreviewResponse.DeepSummary(
                        deep,
                        deep ? "COMPLETE" : null,
                        deep ? "READY" : null,
                        0,
                        0,
                        0,
                        0
                ),
                deep,
                deep
                        ? List.of(new RuntimeConfigurationWorkbenchPreviewResponse.ArtifactSummary(
                                "runtime-configuration/configuration-tree.yaml",
                                "application/yaml",
                                512,
                                false
                        ))
                        : List.of(),
                List.of()
        );
    }
}
