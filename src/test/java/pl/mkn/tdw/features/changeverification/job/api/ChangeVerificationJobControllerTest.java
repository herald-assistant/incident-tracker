package pl.mkn.tdw.features.changeverification.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.changeverification.job.ChangeVerificationJobService;
import pl.mkn.tdw.features.changeverification.job.error.ChangeVerificationJobNotFoundException;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                                  "modes": ["CHECK_COMPLIANCE", "GENERATE_SMOKE_PACK"],
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
                .andExpect(jsonPath("$.modes[0]").value("CHECK_COMPLIANCE"))
                .andExpect(jsonPath("$.modes[1]").value("GENERATE_SMOKE_PACK"))
                .andExpect(jsonPath("$.checkStoryCompliance").value(true))
                .andExpect(jsonPath("$.checkInstructionCompliance").value(true))
                .andExpect(jsonPath("$.aiModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.reasoningEffort").value("medium"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].code").value("SOURCE_DISCOVERY"))
                .andExpect(jsonPath("$.steps[0].producesEvidence[0].provider").value("change-verification"))
                .andExpect(jsonPath("$.result.compliance.status").value("PASSED_WITH_WARNINGS"))
                .andExpect(jsonPath("$.result.smokePack.requested").value(true))
                .andExpect(jsonPath("$.result.execution.requested").value(false));

        verify(changeVerificationJobService).startJob(new ChangeVerificationJobStartRequest(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE, ChangeVerificationJobMode.GENERATE_SMOKE_PACK),
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
                                  "modes": ["CHECK_COMPLIANCE"]
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
                .andExpect(jsonPath("$.preparedPrompt").value("Change Verification skeleton prompt"));

        verify(changeVerificationJobService).getJob("job-123");
    }

    @Test
    void shouldReturnSmokePack() throws Exception {
        when(changeVerificationJobService.getSmokePack("job-123")).thenReturn(smokePack());

        mockMvc.perform(get("/api/change-verification/jobs/job-123/smoke-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.tests[0].id").value("smoke-001"));

        verify(changeVerificationJobService).getSmokePack("job-123");
    }

    @Test
    void shouldUpdateSmokePack() throws Exception {
        when(changeVerificationJobService.updateSmokePack(any(), any())).thenReturn(smokePack());

        mockMvc.perform(put("/api/change-verification/jobs/job-123/smoke-pack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requested": true,
                                  "status": "READY",
                                  "postmanCollectionName": "CRM-123 smoke verification",
                                  "tests": [],
                                  "visibilityLimits": [],
                                  "suggestedActions": [],
                                  "confidence": "medium"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postmanCollectionName").value("CRM-123 smoke verification"));
    }

    @Test
    void shouldReturnPostmanCollection() throws Exception {
        when(changeVerificationJobService.postmanCollection("job-123")).thenReturn(Map.of(
                "info", Map.of("name", "CRM-123 smoke verification"),
                "item", List.of()
        ));

        mockMvc.perform(get("/api/change-verification/jobs/job-123/postman/collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.name").value("CRM-123 smoke verification"));

        verify(changeVerificationJobService).postmanCollection("job-123");
    }

    @Test
    void shouldExecuteSmokePack() throws Exception {
        when(changeVerificationJobService.executeSmokePack(any(), any())).thenReturn(execution());

        mockMvc.perform(post("/api/change-verification/jobs/job-123/smoke-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "https://test.example.com",
                                  "selectedTestIds": ["smoke-001"],
                                  "variables": {"customerId": "123"},
                                  "executeCleanup": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.testResults[0].testId").value("smoke-001"));
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
        var modes = List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE, ChangeVerificationJobMode.GENERATE_SMOKE_PACK);
        return new ChangeVerificationJobStateSnapshot(
                jobId,
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                modes,
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
                                "SMOKE_PACK_GENERATION",
                                "Smoke pack generation",
                                "AI",
                                "COMPLETED",
                                "Smoke pack placeholder.",
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
                new ChangeVerificationResultResponse(
                        "COMPLETED",
                        "CRM-123",
                        "https://jira.example.com/browse/CRM-123",
                        modes,
                        "Change Verification skeleton prompt",
                        new ChangeVerificationComplianceResponse(
                                true,
                                true,
                                "PASSED_WITH_WARNINGS",
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        smokePack(),
                        skippedExecution(),
                        null
                )
        );
    }

    private static ChangeVerificationExecutionResponse execution() {
        return new ChangeVerificationExecutionResponse(
                true,
                "PASSED",
                List.of("smoke-001"),
                List.of(new ChangeVerificationSmokeTestExecutionResponse(
                        "smoke-001",
                        "Customer profile exposes status",
                        "PASSED",
                        new ChangeVerificationSmokeHttpResultResponse(
                                "GET",
                                "https://test.example.com/api/customers/123",
                                200,
                                42,
                                "{}",
                                List.of(),
                                null
                        ),
                        List.of(new ChangeVerificationSmokeAssertionResultResponse(
                                "STATUS",
                                "status",
                                "PASSED",
                                "Expected HTTP status 200, got 200."
                        )),
                        List.of(),
                        new ChangeVerificationSmokeCleanupResultResponse(
                                "NONE",
                                "SKIPPED",
                                null,
                                null,
                                "No cleanup requested."
                        )
                )),
                List.of("NONE: SKIPPED"),
                null,
                List.of()
        );
    }

    private static ChangeVerificationExecutionResponse skippedExecution() {
        return new ChangeVerificationExecutionResponse(
                false,
                "SKIPPED",
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        );
    }

    private static ChangeVerificationSmokePackResponse smokePack() {
        return new ChangeVerificationSmokePackResponse(
                true,
                "READY",
                "CRM-123 smoke verification",
                List.of(new ChangeVerificationSmokeTestResponse(
                        "smoke-001",
                        "Customer profile exposes status",
                        "GET",
                        "/api/customers/{{customerId}}",
                        "Verify status field for active customer.",
                        List.of(new ChangeVerificationNameValueResponse("Accept", "application/json", true)),
                        List.of(),
                        null,
                        List.of(new ChangeVerificationSmokeAssertionResponse("STATUS", "status", "EQUALS", "200")),
                        List.of("select status from customer where id = :customerId"),
                        List.of(),
                        new ChangeVerificationSmokeCleanupResponse("NONE", null, null, null, null, List.of()),
                        List.of("No cleanup needed for readonly GET."),
                        List.of("change-verification/merge-requests.md"),
                        "Acceptance criterion: status is returned.",
                        "READY"
                )),
                List.of(),
                List.of("Review customerId environment variable."),
                "medium"
        );
    }
}
