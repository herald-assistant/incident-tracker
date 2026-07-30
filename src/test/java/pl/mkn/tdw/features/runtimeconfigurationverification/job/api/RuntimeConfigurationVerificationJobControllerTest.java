package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.RuntimeConfigurationVerificationJobService;
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
                "clp-backend",
                "dev1",
                "zt001",
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
                                  "systemId": " clp-backend ",
                                  "sourceBranch": " dev1 ",
                                  "targetBranch": " zt001 ",
                                  "codeRef": " release/2026.07 ",
                                  "model": " gpt-5.4 ",
                                  "reasoningEffort": " medium "
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.mode").value("DEEP"))
                .andExpect(jsonPath("$.repositoryId").value("runtime-config"))
                .andExpect(jsonPath("$.systemId").value("clp-backend"))
                .andExpect(jsonPath("$.sourceBranch").value("dev1"))
                .andExpect(jsonPath("$.targetBranch").value("zt001"))
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
                "clp-backend",
                "dev2",
                "zt004",
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
                                  "systemId": "clp-backend",
                                  "sourceBranch": "dev2",
                                  "targetBranch": "zt004"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("BASIC"))
                .andExpect(jsonPath("$.codeRef").doesNotExist());

        verify(jobService).startJob(request);
    }

    @Test
    void shouldExposeSafeScopeValidationError() throws Exception {
        var request = new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "clp-backend",
                "dev1",
                "zt001",
                null,
                null,
                null
        );
        when(jobService.startJob(request)).thenThrow(
                RuntimeConfigurationScopeException.configurationDirectoryMissing("clp-backend")
        );

        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "BASIC",
                                  "repositoryId": "runtime-config",
                                  "systemId": "clp-backend",
                                  "sourceBranch": "dev1",
                                  "targetBranch": "zt001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUNTIME_CONFIGURATION_DIRECTORY_MISSING"))
                .andExpect(jsonPath("$.message").value(
                        "Operational Context system has no configuration-directory runtime signal: clp-backend"
                ));
    }

    @Test
    void shouldRejectUnsupportedBranch() throws Exception {
        mockMvc.perform(post("/api/runtime-configuration-verification/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("""
                                "sourceBranch": "dev10",
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
                                  "systemId": "clp-backend",
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
                  "systemId": "clp-backend",
                  %s
                }
                """.formatted(branchFields);
    }

    private static RuntimeConfigurationVerificationJobStartRequest request() {
        return new RuntimeConfigurationVerificationJobStartRequest(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "clp-backend",
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
                request.systemId(),
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
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }
}
