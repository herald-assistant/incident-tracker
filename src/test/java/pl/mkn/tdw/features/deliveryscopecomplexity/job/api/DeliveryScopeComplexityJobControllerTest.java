package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.DeliveryScopeComplexityJobService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryScopeComplexityJobControllerTest {

    private final DeliveryScopeComplexityJobService service = mock(DeliveryScopeComplexityJobService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryScopeComplexityJobController(service)).build();
    }

    @Test
    void shouldStartAndReadFeatureJob() throws Exception {
        var snapshot = snapshot();
        when(service.startJob(any())).thenReturn(snapshot);
        when(service.getJob("job-1")).thenReturn(snapshot);

        mockMvc.perform(post("/api/delivery-scope-complexity/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jiraProject":"CRM","fromDate":"2026-07-01","toDate":"2026-07-31","model":"gpt-5","reasoningEffort":"medium"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/delivery-scope-complexity/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraProject").value("CRM"));
    }

    @Test
    void shouldRejectBlankProjectAndRawJql() throws Exception {
        mockMvc.perform(post("/api/delivery-scope-complexity/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jiraProject":"project = CRM","fromDate":"2026-07-01","toDate":"2026-07-31","model":"gpt-5"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private DeliveryScopeComplexityJobStateSnapshot snapshot() {
        var now = Instant.parse("2026-07-01T10:00:00Z");
        return new DeliveryScopeComplexityJobStateSnapshot(
                "job-1", "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                "gpt-5", "medium", "QUEUED", "QUEUED", "Queued", null, null,
                now, now, null, 0, 0, 0, null, List.of(), List.of(), List.of(), List.of(),
                new DeliveryScopeAggregateResponse(
                        0.0, 0.0, 0, 0, 0, 0, 0, 0, "LOW", null
                )
        );
    }
}
