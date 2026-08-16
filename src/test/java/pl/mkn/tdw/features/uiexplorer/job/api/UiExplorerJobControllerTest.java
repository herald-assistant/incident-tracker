package pl.mkn.tdw.features.uiexplorer.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerProfile;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.features.uiexplorer.job.UiExplorerJobService;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerExportUnavailableException;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportEnvelope;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiExplorerJobController.class)
class UiExplorerJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UiExplorerJobService uiExplorerJobService;

    @MockitoBean
    private UiExplorerExportService uiExplorerExportService;

    @Test
    void shouldAcceptUiExplorerJobAndExposeQueuedAsyncState() throws Exception {
        when(uiExplorerJobService.startJob(any(UiExplorerJobStartRequest.class)))
                .thenReturn(snapshot("crm-ui-job-123"));

        mockMvc.perform(post("/api/ui-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("crm-ui-job-123"))
                .andExpect(jsonPath("$.request.systemId").value("crm-agent-portal"))
                .andExpect(jsonPath("$.request.branch").value("main"))
                .andExpect(jsonPath("$.request.screenId").value("crm-contact-preferences"))
                .andExpect(jsonPath("$.request.sourceRevision").value("crm-commit-abc123"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.sourceRevision.revision").value("crm-commit-abc123"))
                .andExpect(jsonPath("$.outputAvailability.status").value("BLOCKED"))
                .andExpect(jsonPath("$.preparedPrompt").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.report").doesNotExist())
                .andExpect(jsonPath("$.exportAvailable").value(false));

        verify(uiExplorerJobService).startJob(new UiExplorerJobStartRequest(
                "crm-agent-portal",
                "main",
                "crm-contact-preferences",
                "crm-commit-abc123",
                UiExplorerProfile.FUNCTIONAL_DOCUMENTATION,
                Map.of(
                        UiExplorerSectionId.OVERVIEW, UiExplorerSectionMode.DEEP,
                        UiExplorerSectionId.FORMS_AND_RULES, UiExplorerSectionMode.COMPACT
                ),
                "Document the strongly anonymized CRM contact preference scenario.",
                "gpt-5.4",
                "medium"
        ));
    }

    @Test
    void shouldRejectRequestWithoutActiveSection() throws Exception {
        mockMvc.perform(post("/api/ui-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemId": "crm-agent-portal",
                                  "branch": "main",
                                  "screenId": "crm-contact-preferences",
                                  "sourceRevision": "crm-commit-abc123",
                                  "profile": "FUNCTIONAL_DOCUMENTATION",
                                  "sectionModes": {
                                    "OVERVIEW": "OFF"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownSectionWithoutFallback() throws Exception {
        mockMvc.perform(post("/api/ui-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemId": "crm-agent-portal",
                                  "branch": "main",
                                  "screenId": "crm-contact-preferences",
                                  "sourceRevision": "crm-commit-abc123",
                                  "profile": "FUNCTIONAL_DOCUMENTATION",
                                  "sectionModes": {
                                    "LEGACY_FORM_SECTION": "DEEP"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireCatalogSourceRevision() throws Exception {
        mockMvc.perform(post("/api/ui-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemId": "crm-agent-portal",
                                  "branch": "main",
                                  "screenId": "crm-contact-preferences",
                                  "profile": "FUNCTIONAL_DOCUMENTATION",
                                  "sectionModes": { "OVERVIEW": "DEEP" }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnStoredJob() throws Exception {
        when(uiExplorerJobService.getJob("crm-ui-job-123")).thenReturn(snapshot("crm-ui-job-123"));

        mockMvc.perform(get("/api/ui-explorer/jobs/crm-ui-job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("crm-ui-job-123"))
                .andExpect(jsonPath("$.currentStepCode").value("SCREEN_DISCOVERY"));
    }

    @Test
    void shouldReturnNotFoundForUnknownJob() throws Exception {
        when(uiExplorerJobService.getJob("crm-missing-job"))
                .thenThrow(new UiExplorerJobNotFoundException("crm-missing-job"));

        mockMvc.perform(get("/api/ui-explorer/jobs/crm-missing-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UI_EXPLORER_JOB_NOT_FOUND"));
    }

    @Test
    void shouldExportCurrentCrmPortableContract() throws Exception {
        when(uiExplorerExportService.export("crm-ui-job-123")).thenReturn(
                UiExplorerExportEnvelope.from(
                        snapshot("crm-ui-job-123"),
                        Instant.parse("2026-08-15T08:05:00Z")
                )
        );

        mockMvc.perform(get("/api/ui-explorer/jobs/crm-ui-job-123/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("tdw.ui-explorer-export"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.payload.type").value("ui-explorer-analysis"))
                .andExpect(jsonPath("$.payload.resultContract").value("ui-explorer-result-v2"));
    }

    @Test
    void shouldRejectCrmExportBeforePublishableOutputExists() throws Exception {
        when(uiExplorerExportService.export("crm-ui-job-running")).thenThrow(
                new UiExplorerExportUnavailableException(
                        "Only a completed UI Explorer run with a result and report can be exported."
                )
        );

        mockMvc.perform(get("/api/ui-explorer/jobs/crm-ui-job-running/export"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UI_EXPLORER_EXPORT_UNAVAILABLE"));
    }

    private static String validRequestJson() {
        return """
                {
                  "systemId": "crm-agent-portal",
                  "branch": "main",
                  "screenId": "crm-contact-preferences",
                  "sourceRevision": "crm-commit-abc123",
                  "profile": "FUNCTIONAL_DOCUMENTATION",
                  "sectionModes": {
                    "OVERVIEW": "DEEP",
                    "FORMS_AND_RULES": "COMPACT"
                  },
                  "scenarioDescription": "Document the strongly anonymized CRM contact preference scenario.",
                  "model": "gpt-5.4",
                  "reasoningEffort": "medium"
                }
                """;
    }

    private static UiExplorerJobStateSnapshot snapshot(String jobId) {
        var now = Instant.parse("2026-08-15T08:00:00Z");
        var availability = new UiExplorerOutputAvailability(
                UiExplorerOutputAvailabilityStatus.BLOCKED,
                "UI_EXPLORER_ANALYSIS_IN_PROGRESS",
                "UI Explorer analysis is still in progress.",
                List.of("SCREEN_DISCOVERY", "SOURCE_CONTEXT", "AI_PREPARATION", "AI_ANALYSIS")
        );
        return new UiExplorerJobStateSnapshot(
                jobId,
                new UiExplorerJobRequestSnapshot(
                        "crm-agent-portal",
                        "CRM Agent Portal",
                        "main",
                        "crm-contact-preferences",
                        "crm-commit-abc123",
                        UiExplorerProfile.FUNCTIONAL_DOCUMENTATION,
                        List.of(new UiExplorerSectionModeAssignment(
                                UiExplorerSectionId.OVERVIEW,
                                UiExplorerSectionMode.DEEP
                        )),
                        "Document a strongly anonymized CRM scenario.",
                        "gpt-5.4",
                        "medium"
                ),
                UiExplorerJobStatus.QUEUED,
                "SCREEN_DISCOVERY",
                "Identify the selected screen",
                null,
                null,
                now,
                now,
                null,
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
                availability,
                false
        );
    }
}
