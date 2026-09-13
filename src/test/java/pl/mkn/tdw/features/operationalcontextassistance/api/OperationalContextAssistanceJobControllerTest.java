package pl.mkn.tdw.features.operationalcontextassistance.api;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.operationalcontextassistance.job.OperationalContextAssistanceJobNotFoundException;
import pl.mkn.tdw.features.operationalcontextassistance.job.OperationalContextAssistanceJobService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalContextAssistanceJobController.class)
class OperationalContextAssistanceJobControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalContextAssistanceJobService jobService;

    @Test
    void startsJobWithAcceptedQueuedSnapshot() throws Exception {
        when(jobService.startJob(any())).thenReturn(snapshot("job-1"));

        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE_AREA","description":"Order Intake przyjmuje zlecenia.",
                                 "gitLabSource":{"project":"demo-app","ref":"main"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.steps[0].code").value("COLLECT_CONTEXT"))
                .andExpect(jsonPath("$.preparedPrompt").doesNotExist());

        verify(jobService).startJob(any(OperationalContextAssistanceJobStartRequest.class));
    }

    @Test
    void acceptsFullProjectUrlButRejectsAmbiguousOrMissingSelection() throws Exception {
        when(jobService.startJob(any())).thenReturn(snapshot("job-url"));

        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE_AREA","description":"Proces obsługi profilu klienta",
                                 "gitLabSource":{"projectUrl":"https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS","ref":"main"}}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE_AREA","description":"Proces obsługi profilu klienta",
                                 "gitLabSource":{"project":"PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS",
                                                 "projectUrl":"https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS","ref":"main"}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE_AREA","description":"Proces obsługi profilu klienta",
                                 "gitLabSource":{"ref":"main"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsRepositoryFactsForEachUsageWithSelectedGitLabSource() throws Exception {
        when(jobService.startJob(any())).thenReturn(snapshot("job-facts"));

        for (String facts : List.of(
                "{\"usage\":\"UNKNOWN\"}",
                "{\"usage\":\"DEPLOYED_SYSTEM\",\"systemName\":\"Customer Profile Process\",\"runtimeServiceName\":\"customer-profile-runtime\"}",
                "{\"usage\":\"SHARED_LIBRARY\",\"systemIds\":[\"consumer-a\",\"consumer-b\"]}",
                "{\"usage\":\"EXISTING_SYSTEM\",\"systemIds\":[\"consumer-a\"]}"
        )) {
            mockMvc.perform(post("/api/operational-context/assistance/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mode":"CREATE_AREA","description":"Podłącz repozytorium",
                                     "gitLabSource":{"project":"demo-app","ref":"main"},
                                     "repositoryFacts":%s}
                                    """.formatted(facts)))
                    .andExpect(status().isAccepted());
        }
    }

    @Test
    void rejectsRepositoryFactsWithoutSourceOrWithInconsistentUsage() throws Exception {
        for (String payload : List.of(
                "{\"mode\":\"CREATE_AREA\",\"description\":\"Repo\", \"repositoryFacts\":{\"usage\":\"UNKNOWN\"}}",
                "{\"mode\":\"CREATE_AREA\",\"description\":\"Repo\", \"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\"}, \"repositoryFacts\":{\"usage\":\"UNKNOWN\",\"systemName\":\"Invented\"}}",
                "{\"mode\":\"CREATE_AREA\",\"description\":\"Repo\", \"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\"}, \"repositoryFacts\":{\"usage\":\"EXISTING_SYSTEM\",\"systemIds\":[]}}",
                "{\"mode\":\"CREATE_AREA\",\"description\":\"Repo\", \"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\"}, \"repositoryFacts\":{\"usage\":\"SHARED_LIBRARY\",\"systemIds\":[\"a\",\"a\"]}}",
                "{\"mode\":\"CREATE_AREA\",\"description\":\"Repo\", \"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\"}, \"repositoryFacts\":{\"usage\":\"SHARED_LIBRARY\",\"legacy\":true}}",
                "{\"mode\":\"IMPROVE_ENTITY\",\"description\":\"Repo\", \"target\":{\"kind\":\"ENTITY\",\"entityType\":\"system\",\"entityId\":\"consumer-a\"}, \"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\"}, \"repositoryFacts\":{\"usage\":\"UNKNOWN\"}}"
        )) {
            mockMvc.perform(post("/api/operational-context/assistance/jobs")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsMissingTargetForImprovement() throws Exception {
        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"mode\":\"IMPROVE_ENTITY\",\"description\":\"Doprecyzuj opis\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnknownRootAndNestedFields() throws Exception {
        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"mode\":\"CREATE_AREA\",\"description\":\"Obszar\",\"legacy\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"mode\":\"CREATE_AREA\",\"description\":\"Obszar\","
                                + "\"gitLabSource\":{\"project\":\"demo-app\",\"ref\":\"main\",\"group\":\"other\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsafeGitLabSelectionAndFindingWithoutId() throws Exception {
        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE_AREA","description":"Obszar",
                                 "gitLabSource":{"project":"../outside","ref":"main"}}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/operational-context/assistance/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"RESOLVE_FINDING","description":"Wyjaśnij brak ownera",
                                 "target":{"kind":"OPEN_QUESTION","entityType":"system","entityId":"order-intake"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsSnapshotAndNotFoundForUnknownId() throws Exception {
        when(jobService.getJob("job-1")).thenReturn(snapshot("job-1"));
        when(jobService.getJob("missing")).thenThrow(new OperationalContextAssistanceJobNotFoundException("missing"));

        mockMvc.perform(get("/api/operational-context/assistance/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));
        mockMvc.perform(get("/api/operational-context/assistance/jobs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPCTX_ASSISTANCE_JOB_NOT_FOUND"));
    }

    @Test
    void previewsAndRecordsOneDecisionForTheWholeBatch() throws Exception {
        when(jobService.previewBatch(any(), any())).thenReturn(new OperationalContextAssistanceBatchPreview(
                "digest-1", "digest-2", true, List.of(), List.of()));
        when(jobService.applyBatch(any(), any())).thenReturn(snapshot("job-1"));

        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"action":"APPLY","selectedPaths":["name"],"confirmedPaths":[]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateDigest").value("digest-2"))
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"action":"APPLY","selectedPaths":["name"],"confirmedPaths":[]}],
                                 "candidateDigest":"digest-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));

        verify(jobService).previewBatch(any(), any(OperationalContextAssistanceBatchReviewRequest.class));
        verify(jobService).applyBatch(any(), any(OperationalContextAssistanceBatchReviewRequest.class));
    }

    @Test
    void acceptsTypedOperatorCorrectionInBatchPreview() throws Exception {
        when(jobService.previewBatch(any(), any())).thenReturn(new OperationalContextAssistanceBatchPreview(
                "digest-1", "edited-digest", true, List.of(), List.of()));

        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"action":"APPLY","selectedPaths":["definition"],
                                  "confirmedPaths":["definition"],
                                  "editedValues":{"definition":"Poprawiona definicja operatora"}}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateDigest").value("edited-digest"));

        var request = ArgumentCaptor.forClass(OperationalContextAssistanceBatchReviewRequest.class);
        verify(jobService).previewBatch(org.mockito.ArgumentMatchers.eq("job-1"), request.capture());
        assertThat(request.getValue().decisions().get(0).editedValues().get("definition").asText())
                .isEqualTo("Poprawiona definicja operatora");
    }

    @Test
    void rejectsDecisionWithUnknownOrMissingAction() throws Exception {
        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"action":"APPLY","selectedPaths":["name"],"legacy":true}],"candidateDigest":"digest-2"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"selectedPaths":["name"]}],"candidateDigest":"digest-2"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[],"legacy":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsStaleProposalAsConflict() throws Exception {
        when(jobService.applyBatch(any(), any())).thenThrow(
                new OperationalContextCatalogMaintenanceException(
                        OperationalContextCatalogMaintenanceException.Code.STALE_PROPOSAL,
                        "Accepted field changed", List.of()
                ));

        mockMvc.perform(post("/api/operational-context/assistance/jobs/job-1/batch/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"action":"APPLY","selectedPaths":["name"]}],"candidateDigest":"digest-2"}
                                """))
                .andExpect(status().isConflict());
    }

    private OperationalContextAssistanceJobSnapshot snapshot(String id) {
        var now = Instant.parse("2026-09-13T10:00:00Z");
        return new OperationalContextAssistanceJobSnapshot(
                id, OperationalContextAssistanceJobStatus.QUEUED,
                "COLLECT_CONTEXT", "Zbierz kontekst", null, null,
                now, now, null,
                List.of(new pl.mkn.tdw.shared.ai.AnalysisJobStepResponse(
                        "COLLECT_CONTEXT", "Zbierz kontekst", "CONTEXT", "PENDING", null,
                        null, null, null, List.of(), List.of(), null
                )),
                List.of(), null, null, null, List.of(), List.of(), null, List.of(), List.of(), null
        );
    }
}
