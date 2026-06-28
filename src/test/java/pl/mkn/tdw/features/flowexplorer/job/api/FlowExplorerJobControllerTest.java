package pl.mkn.tdw.features.flowexplorer.job.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.job.FlowExplorerJobService;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlowExplorerJobController.class)
class FlowExplorerJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowExplorerJobService flowExplorerJobService;

    @Test
    void shouldStartFlowExplorerJob() throws Exception {
        when(flowExplorerJobService.startJob(any(FlowExplorerJobStartRequest.class)))
                .thenReturn(snapshot("job-123"));

        mockMvc.perform(post("/api/flow-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemId": "crm-service",
                                  "endpointId": "GET:/api/customers/{id}",
                                  "branch": "feature/FLOW-42",
                                  "goal": "DEEP_DISCOVERY",
                                  "focusAreas": ["FUNCTIONAL_FLOW"],
                                  "sectionModes": [
                                    {"id": "FUNCTIONAL_FLOW", "mode": "DEEP"},
                                    {"id": "VALIDATIONS", "mode": "COMPACT"},
                                    {"id": "PERSISTENCE", "mode": "OFF"},
                                    {"id": "INTEGRATIONS", "mode": "COMPACT"}
                                  ],
                                  "userInstructions": "Opisz to jezykiem testera.",
                                  "model": "gpt-5.4",
                                  "reasoningEffort": "medium"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.systemId").value("crm-service"))
                .andExpect(jsonPath("$.endpointId").value("GET:/api/customers/{id}"))
                .andExpect(jsonPath("$.branch").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.goal").value("DEEP_DISCOVERY"))
                .andExpect(jsonPath("$.focusAreas[0]").value("FUNCTIONAL_FLOW"))
                .andExpect(jsonPath("$.sectionModes[0].id").value("FUNCTIONAL_FLOW"))
                .andExpect(jsonPath("$.sectionModes[0].mode").value("DEEP"))
                .andExpect(jsonPath("$.aiModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.reasoningEffort").value("medium"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.steps[0].phase").value("CONTEXT"))
                .andExpect(jsonPath("$.steps[0].itemCount").value(1))
                .andExpect(jsonPath("$.steps[0].producesEvidence[0].provider").value("flow-explorer"))
                .andExpect(jsonPath("$.contextSections[0].provider").value("flow-explorer"))
                .andExpect(jsonPath("$.contextSnapshot.coverage.endpointResolved").value(true))
                .andExpect(jsonPath("$.contextSnapshot.flowNodes[0].methods[0].methodName").value("getCustomer"))
                .andExpect(jsonPath("$.contextSnapshot.snippetCards[0].content").value(org.hamcrest.Matchers.containsString("getCustomer")))
                .andExpect(jsonPath("$.result.goal").value("DEEP_DISCOVERY"))
                .andExpect(jsonPath("$.result.aiResponse.overview.markdown").value("Context ready."))
                .andExpect(jsonPath("$.result.aiResponse.sections[0].id").value("FUNCTIONAL_FLOW"))
                .andExpect(jsonPath("$.result.aiResponse.sections[0].mode").value("DEEP"));

        verify(flowExplorerJobService).startJob(new FlowExplorerJobStartRequest(
                "crm-service",
                "GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                List.of(
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                                FlowExplorerResultSectionMode.DEEP
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.VALIDATIONS,
                                FlowExplorerResultSectionMode.COMPACT
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.PERSISTENCE,
                                FlowExplorerResultSectionMode.OFF
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.INTEGRATIONS,
                                FlowExplorerResultSectionMode.COMPACT
                        )
                ),
                "Opisz to jezykiem testera.",
                "gpt-5.4",
                "medium"
        ));
    }

    @Test
    void shouldRejectMissingEndpointSelector() throws Exception {
        mockMvc.perform(post("/api/flow-explorer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "systemId": "crm-service"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endpointSelectorPresent"));
    }

    @Test
    void shouldReturnFlowExplorerJobSnapshot() throws Exception {
        when(flowExplorerJobService.getJob("job-123")).thenReturn(snapshot("job-123"));

        mockMvc.perform(get("/api/flow-explorer/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.preparedPrompt").value("Flow Explorer canonical prompt"))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));

        verify(flowExplorerJobService).getJob("job-123");
    }

    @Test
    void shouldStartFlowExplorerFollowUpChatMessage() throws Exception {
        when(flowExplorerJobService.startChatMessage(any(String.class), any(FlowExplorerChatMessageRequest.class)))
                .thenReturn(snapshotWithChat("job-123"));

        mockMvc.perform(post("/api/flow-explorer/jobs/job-123/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Gdzie jest walidacja?"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.chatMessages", hasSize(2)))
                .andExpect(jsonPath("$.chatMessages[0].role").value("USER"))
                .andExpect(jsonPath("$.chatMessages[0].content").value("Gdzie jest walidacja?"))
                .andExpect(jsonPath("$.chatMessages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.chatMessages[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.chatMessages[1].content").value("Walidacja jest w CustomerService.validate."));

        verify(flowExplorerJobService).startChatMessage(
                "job-123",
                new FlowExplorerChatMessageRequest("Gdzie jest walidacja?")
        );
    }

    @Test
    void shouldApplyFlowExplorerResultUpdate() throws Exception {
        when(flowExplorerJobService.applyResultUpdate(
                any(String.class),
                any(String.class),
                any(FlowExplorerResultUpdateDecisionRequest.class)
        )).thenReturn(snapshot("job-123"));

        mockMvc.perform(post("/api/flow-explorer/jobs/job-123/chat/messages/message-2/result-update/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultUpdateDecisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.result.aiResponse.overview.markdown").value("Context ready."));

        verify(flowExplorerJobService).applyResultUpdate(
                eq("job-123"),
                eq("message-2"),
                any(FlowExplorerResultUpdateDecisionRequest.class)
        );
    }

    @Test
    void shouldRejectFlowExplorerResultUpdate() throws Exception {
        when(flowExplorerJobService.rejectResultUpdate(
                any(String.class),
                any(String.class),
                any(FlowExplorerResultUpdateDecisionRequest.class)
        )).thenReturn(snapshot("job-123"));

        mockMvc.perform(post("/api/flow-explorer/jobs/job-123/chat/messages/message-2/result-update/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultUpdateDecisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.result.aiResponse.overview.markdown").value("Context ready."));

        verify(flowExplorerJobService).rejectResultUpdate(
                eq("job-123"),
                eq("message-2"),
                any(FlowExplorerResultUpdateDecisionRequest.class)
        );
    }

    @Test
    void shouldReturnNotFoundWhenJobIsMissing() throws Exception {
        when(flowExplorerJobService.getJob("missing-job"))
                .thenThrow(new FlowExplorerJobNotFoundException("missing-job"));

        mockMvc.perform(get("/api/flow-explorer/jobs/missing-job"))
                .andExpect(status().isNotFound());

        verify(flowExplorerJobService).getJob("missing-job");
    }

    private static FlowExplorerJobStateSnapshot snapshot(String jobId) {
        return snapshot(jobId, List.of());
    }

    private static FlowExplorerJobStateSnapshot snapshotWithChat(String jobId) {
        var instant = Instant.parse("2026-04-12T18:00:00Z");
        return snapshot(jobId, List.of(
                new AnalysisChatMessageResponse(
                        "message-1",
                        "USER",
                        "COMPLETED",
                        "Gdzie jest walidacja?",
                        null,
                        null,
                        instant,
                        instant,
                        instant,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                new AnalysisChatMessageResponse(
                        "message-2",
                        "ASSISTANT",
                        "COMPLETED",
                        "Walidacja jest w CustomerService.validate.",
                        null,
                        null,
                        instant,
                        instant,
                        instant,
                        List.of(),
                        List.of(),
                        List.of(),
                        "Gdzie jest walidacja?",
                        null
                )
        ));
    }

    private static FlowExplorerJobStateSnapshot snapshot(String jobId, List<AnalysisChatMessageResponse> chatMessages) {
        var instant = Instant.parse("2026-04-12T18:00:00Z");
        return new FlowExplorerJobStateSnapshot(
                jobId,
                "crm-service",
                "GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                FlowExplorerResultSectionModeResolver.resolve(List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW)),
                "gpt-5.4",
                "medium",
                "COMPLETED",
                "DETERMINISTIC_CONTEXT",
                "Deterministic endpoint context",
                null,
                null,
                instant,
                instant,
                instant,
                List.of(new AnalysisJobStepResponse(
                        "DETERMINISTIC_CONTEXT",
                        "Deterministic endpoint context",
                        "CONTEXT",
                        "COMPLETED",
                        "Context done.",
                        1,
                        instant,
                        instant,
                        List.of(),
                        List.of(new AnalysisEvidenceReference("flow-explorer", "endpoint-context"))
                )),
                contextSnapshot(),
                List.of(new AnalysisEvidenceSection(
                        "flow-explorer",
                        "endpoint-context",
                        List.of(new AnalysisEvidenceItem(
                                "Context coverage",
                                List.of(new AnalysisEvidenceAttribute("flowNodeCount", "1"))
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                chatMessages,
                "Flow Explorer canonical prompt",
                new FlowExplorerResultResponse(
                        "COMPLETED",
                        "crm-service",
                        "GET:/api/customers/{id}",
                        null,
                        null,
                        "feature/FLOW-42",
                        FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                        "Flow Explorer canonical prompt",
                        aiResponse(),
                        null
                )
        );
    }

    private static String resultUpdateDecisionJson() {
        return """
                {
                  "aiResponse": {
                    "goal": "DEEP_DISCOVERY",
                    "audience": "business_or_system_analyst_tester",
                    "overview": {
                      "markdown": "Context ready.",
                      "confidence": "high",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                    },
                    "sections": [
                      {
                        "id": "FUNCTIONAL_FLOW",
                        "title": "Functional flow",
                        "mode": "DEEP",
                        "markdown": "Controller przyjmuje request.",
                        "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                        "visibilityLimits": [],
                        "openQuestions": []
                      }
                    ],
                    "globalVisibilityLimits": [],
                    "globalOpenQuestions": [],
                    "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                    "confidence": "high",
                    "followUpPrompts": []
                  }
                }
                """;
    }

    private static FlowExplorerAiResponse aiResponse() {
        return new FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview(
                        "Context ready.",
                        "high",
                        List.of("crm-service:CustomerController.java:L12-L24")
                ),
                List.of(new FlowExplorerResultSection(
                        FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                        "Functional flow",
                        FlowExplorerResultSectionMode.DEEP,
                        "Controller przyjmuje request.",
                        List.of("crm-service:CustomerController.java:L12-L24"),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of("crm-service:CustomerController.java:L12-L24"),
                "high"
        );
    }

    private static FlowExplorerContextSnapshot contextSnapshot() {
        return new FlowExplorerContextSnapshot(
                "crm-service",
                "CRM Service",
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                "GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                new FlowExplorerEndpointContext(
                        "GET:/api/customers/{id}",
                        List.of("GET"),
                        "/api/customers/{id}",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer",
                        "src/main/java/com/example/CustomerController.java",
                        12,
                        24,
                        "HIGH"
                ),
                List.of(new FlowExplorerRepositoryContext(
                        "crm-service",
                        "crm-service",
                        "platform/backend/crm-service",
                        "feature/FLOW-42",
                        true,
                        true,
                        List.of()
                )),
                List.of(new FlowExplorerFlowNode(
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        "src/main/java/com/example/CustomerController.java",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        "Endpoint handler.",
                        "HIGH",
                        List.of()
                )),
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/CustomerController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        false,
                        "Endpoint handler.",
                        "// file: src/main/java/com/example/CustomerController.java\npublic CustomerResponse getCustomer() {}",
                        0,
                        List.of()
                )),
                List.of(),
                List.of(),
                new FlowExplorerContextCoverage(
                        true,
                        1,
                        1,
                        1,
                        1,
                        0,
                        1,
                        103,
                        false,
                        0,
                        0,
                        false,
                        false,
                        false,
                        "HIGH"
                )
        );
    }
}
