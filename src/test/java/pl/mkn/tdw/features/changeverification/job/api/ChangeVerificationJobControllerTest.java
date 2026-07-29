package pl.mkn.tdw.features.changeverification.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.changeverification.job.ChangeVerificationJobService;
import pl.mkn.tdw.features.changeverification.job.error.ChangeVerificationJobNotFoundException;
import pl.mkn.tdw.features.changeverification.job.report.ChangeVerificationReportMapper;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;

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

@WebMvcTest(ChangeVerificationJobController.class)
class ChangeVerificationJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChangeVerificationJobService changeVerificationJobService;

    @Test
    void shouldStartChangeVerificationJob() throws Exception {
        when(changeVerificationJobService.startJob(any(ChangeVerificationJobStartRequest.class)))
                .thenReturn(snapshot("job-123"));

        mockMvc.perform(post("/api/change-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueKey": "CRM-123",
                                  "issueUrl": "https://jira.example.com/browse/CRM-123",
                                  "checkStoryCompliance": true,
                                  "checkInstructionCompliance": true,
                                  "userInstructions": "Skup sie na kryteriach akceptacji.",
                                  "model": "gpt-5.4",
                                  "reasoningEffort": "medium"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.issueKey").value("CRM-123"))
                .andExpect(jsonPath("$.issueUrl").value("https://jira.example.com/browse/CRM-123"))
                .andExpect(jsonPath("$.checkStoryCompliance").value(true))
                .andExpect(jsonPath("$.checkInstructionCompliance").value(true))
                .andExpect(jsonPath("$.aiModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.reasoningEffort").value("medium"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].code").value("SOURCE_DISCOVERY"))
                .andExpect(jsonPath("$.steps[0].producesEvidence[0].provider").value("change-verification"))
                .andExpect(jsonPath("$.result.compliance.status").value("PASSED_WITH_WARNINGS"))
                .andExpect(jsonPath("$.report.header").value("Change Verification: CRM-123"))
                .andExpect(jsonPath("$.report.sections[0].id").value("STORY_COMPLIANCE"))
                .andExpect(jsonPath("$.report.sections[1].id").value("INSTRUCTION_COMPLIANCE"))
                .andExpect(jsonPath("$.report.meta.references[0].type").value("jira"));

        verify(changeVerificationJobService).startJob(new ChangeVerificationJobStartRequest(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                true,
                true,
                "Skup sie na kryteriach akceptacji.",
                "gpt-5.4",
                "medium"
        ));
    }

    @Test
    void shouldRejectMissingIssueSource() throws Exception {
        mockMvc.perform(post("/api/change-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("issueSourcePresent"));
    }

    @Test
    void shouldReturnChangeVerificationJobSnapshot() throws Exception {
        when(changeVerificationJobService.getJob("job-123")).thenReturn(snapshot("job-123"));

        mockMvc.perform(get("/api/change-verification/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.preparedPrompt").value("Change Verification skeleton prompt"))
                .andExpect(jsonPath("$.report.markdownSummary").exists());

        verify(changeVerificationJobService).getJob("job-123");
    }

    @Test
    void shouldRejectRequestWithoutComplianceScope() throws Exception {
        mockMvc.perform(post("/api/change-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueKey": "CRM-123",
                                  "checkStoryCompliance": false,
                                  "checkInstructionCompliance": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnNotFoundWhenJobIsMissing() throws Exception {
        when(changeVerificationJobService.getJob("missing-job"))
                .thenThrow(new ChangeVerificationJobNotFoundException("missing-job"));

        mockMvc.perform(get("/api/change-verification/jobs/missing-job"))
                .andExpect(status().isNotFound());

        verify(changeVerificationJobService).getJob("missing-job");
    }

    private static ChangeVerificationJobStateSnapshot snapshot(String jobId) {
        var instant = Instant.parse("2026-07-25T10:00:00Z");
        var result = new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "Change Verification skeleton prompt",
                new ChangeVerificationComplianceResponse(
                        true,
                        true,
                        "PASSED_WITH_WARNINGS",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                null
        );
        return new ChangeVerificationJobStateSnapshot(
                jobId,
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                true,
                true,
                "gpt-5.4",
                "medium",
                "COMPLETED",
                null,
                null,
                null,
                null,
                instant,
                instant,
                instant,
                List.of(
                        new AnalysisJobStepResponse(
                                "SOURCE_DISCOVERY",
                                "Source discovery",
                                "CONTEXT",
                                "COMPLETED",
                                "Context done.",
                                1,
                                instant,
                                instant,
                                List.of(),
                                List.of(new AnalysisEvidenceReference(
                                        "change-verification",
                                        "change-context-placeholder"
                                ))
                        ),
                        new AnalysisJobStepResponse(
                                "AI_VERIFICATION",
                                "AI verification",
                                "AI",
                                "COMPLETED",
                                "Compliance verified.",
                                null,
                                instant,
                                instant,
                                List.of(),
                                List.of()
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                "Change Verification skeleton prompt",
                result,
                ChangeVerificationReportMapper.toReport(result)
        );
    }
}
