package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.RuntimeConfigurationVerificationJobService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model
        .RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationJobNotFoundException;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigurationVerificationJobController.class)
class RuntimeConfigurationVerificationJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigurationVerificationJobService jobService;

    @Test
    void shouldStartQueuedDeepVerificationJob() throws Exception {
        var request = new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                java.util.List.of("crm-backend"),
                "dev12",
                "uat345",
                "release/2026.07",
                "gpt-5.4",
                "medium"
        );
        when(jobService.startJob(request)).thenReturn(snapshot("job-123", request));

        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "DEEP",
                                  "repositoryId": " runtime-config ",
                                  "systemIds": [" crm-backend "],
                                  "sourceBranch": " dev12 ",
                                  "targetBranch": " uat345 ",
                                  "codeRef": " release/2026.07 ",
                                  "model": " gpt-5.4 ",
                                  "reasoningEffort": " medium "
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.mode").value("DEEP"))
                .andExpect(jsonPath("$.repositoryId").value("runtime-config"))
                .andExpect(jsonPath("$.systemIds[0]").value("crm-backend"))
                .andExpect(jsonPath("$.sourceBranch").value("dev12"))
                .andExpect(jsonPath("$.targetBranch").value("uat345"))
                .andExpect(jsonPath("$.codeRef").value("release/2026.07"))
                .andExpect(jsonPath("$.aiModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.reasoningEffort").value("medium"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.steps").isEmpty())
                .andExpect(jsonPath("$.preparedPrompt").doesNotExist());

        verify(jobService).startJob(request);
    }

    @Test
    void shouldAcceptBasicModeWithoutCodeReference() throws Exception {
        var request = new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                java.util.List.of("crm-backend"),
                "dev2",
                "uat2",
                null,
                null,
                null
        );
        when(jobService.startJob(request)).thenReturn(snapshot("job-basic", request));

        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": ["crm-backend"],
                                  "sourceBranch": "dev2",
                                  "targetBranch": "uat2"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.codeRef").doesNotExist());

        verify(jobService).startJob(request);
    }

    @Test
    void shouldRejectDeepOnlyInputsInBasicMode() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": ["crm-backend"],
                                  "sourceBranch": "dev2",
                                  "targetBranch": "zt004",
                                  "codeRef": "release/2026.07",
                                  "model": "gpt-5.4",
                                  "reasoningEffort": "medium"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectEmptySystemSelection() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": [],
                                  "sourceBranch": "dev1",
                                  "targetBranch": "uat1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectDuplicateSystemSelectionAfterNormalization() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": ["crm-backend", " crm-backend "],
                                  "sourceBranch": "dev1",
                                  "targetBranch": "uat1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectMoreThanFiftySystems() throws Exception {
        var systemIds = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "\"system-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": [%s],
                                  "sourceBranch": "dev1",
                                  "targetBranch": "uat1"
                                }
                                """.formatted(systemIds)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldExposeSafeScopeValidationError() throws Exception {
        var request = new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                java.util.List.of("crm-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
        when(jobService.startJob(request)).thenThrow(
                RuntimeConfigurationScopeException.configurationDirectoryMissing("crm-backend")
        );

        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemIds": ["crm-backend"],
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUNTIME_CONFIGURATION_DIRECTORY_MISSING"))
                .andExpect(jsonPath("$.message").value(
                        "Operational Context system has no configuration-directory runtime signal: crm-backend"
                ));
    }

    @Test
    void shouldRejectUnsafeBranch() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("""
                                "sourceBranch": "dev@unsafe",
                                "targetBranch": "zt001"
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectIdenticalBranches() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("""
                                "sourceBranch": "dev3",
                                "targetBranch": "dev3"
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectMissingCanonicalScope() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "sourceBranch": "dev3",
                                  "targetBranch": "zt003"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnsafeCodeReference() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("""
                                "sourceBranch": "dev3",
                                "targetBranch": "zt003",
                                "codeRef": "release/../secret"
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownMode() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "EXTREME",
                                  "repositoryId": "runtime-config",
                                  "systemIds": ["crm-backend"],
                                  "sourceBranch": "dev3",
                                  "targetBranch": "zt003"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnQueuedJobSnapshot() throws Exception {
        var request = request();
        when(jobService.getJob("job-123")).thenReturn(snapshot("job-123", request));

        mockMvc.perform(get("/api/runtime-configuration-verification/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        verify(jobService).getJob("job-123");
    }

    @Test
    void shouldReturnConfigurationDiffInCompletedBasicJob() throws Exception {
        var request = request();
        var snapshot = completedSnapshot("job-completed", request);
        when(jobService.getJob("job-completed")).thenReturn(snapshot);

        mockMvc.perform(get("/api/runtime-configuration-verification/jobs/job-completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.components[0].result.mode").value("BASIC"))
                .andExpect(jsonPath("$.components[0].result.configurationDiff.sourceBranch").value("dev1"))
                .andExpect(jsonPath("$.components[0].result.configurationDiff.targetBranch").value("zt001"))
                .andExpect(jsonPath("$.components[0].result.configurationDiff.files").isEmpty())
                .andExpect(jsonPath("$.components[0].result.aiSecondOpinion").doesNotExist())
                .andExpect(jsonPath("$.components[0].result.prompt").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundWhenJobIsMissing() throws Exception {
        when(jobService.getJob("missing-job"))
                .thenThrow(new RuntimeConfigurationVerificationJobNotFoundException("missing-job"));

        mockMvc.perform(get("/api/runtime-configuration-verification/jobs/missing-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNTIME_CONFIGURATION_VERIFICATION_JOB_NOT_FOUND"));
    }

    private static String validRequest(String branchFields) {
        return """
                {
                  "mode": "DEEP",
                  "repositoryId": "runtime-config",
                  "systemIds": ["crm-backend"],
                  %s
                }
                """.formatted(branchFields);
    }

    private static RuntimeConfigurationVerificationJobStartRequest request() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                java.util.List.of("crm-backend"),
                "dev1",
                "zt001",
                null,
                null,
                null
        );
    }

    private static RuntimeConfigurationVerificationJobStateSnapshot snapshot(
            String jobId,
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        var now = Instant.parse("2026-07-30T10:00:00Z");
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                jobId,
                request.mode(),
                request.repositoryId(),
                request.systemIds(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                request.model(),
                request.reasoningEffort(),
                "QUEUED",
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                List.of(),
                List.of(new RuntimeConfigurationComponentRunSnapshot(
                        jobId + ":0",
                        request.componentSystemId(),
                        "CRM backend",
                        "backend",
                        "QUEUED",
                        null,
                        null,
                        null,
                        null,
                        now,
                        now,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null
                )),
                false
        );
    }

    private static RuntimeConfigurationVerificationJobStateSnapshot completedSnapshot(
            String jobId,
            RuntimeConfigurationVerificationJobStartRequest request
    ) {
        var now = Instant.parse("2026-07-30T10:00:00Z");
        var result = new RuntimeConfigurationVerificationResult(
                RuntimeConfigurationVerificationStatus.NO_BLOCKING_ANOMALIES,
                RuntimeConfigurationVerificationMode.BASIC,
                new RuntimeConfigurationDeterministicContext(
                        request.repositoryId(),
                        request.componentSystemId(),
                        "CRM backend",
                        "backend",
                        request.sourceBranch(),
                        request.targetBranch(),
                        RuntimeConfigurationDeterministicStatus.NO_BLOCKING_ANOMALIES,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new RuntimeConfigurationDiffProjection("dev1", "zt001", List.of()),
                List.of(),
                null,
                null,
                null,
                List.of(),
                null,
                null
        );
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                jobId,
                request.mode(),
                request.repositoryId(),
                request.systemIds(),
                request.sourceBranch(),
                request.targetBranch(),
                null,
                null,
                null,
                "COMPLETED",
                null,
                null,
                null,
                null,
                now.minusSeconds(10),
                now,
                now,
                List.of(),
                List.of(new RuntimeConfigurationComponentRunSnapshot(
                        jobId + ":0",
                        request.componentSystemId(),
                        "CRM backend",
                        "backend",
                        "COMPLETED",
                        null,
                        null,
                        null,
                        null,
                        now.minusSeconds(10),
                        now,
                        now,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        result,
                        null
                )),
                false
        );
    }
}
