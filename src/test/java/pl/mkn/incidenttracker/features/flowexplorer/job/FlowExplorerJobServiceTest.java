package pl.mkn.incidenttracker.features.flowexplorer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunAssembly;
import pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotToolAccessPolicy;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextRequest;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerChatMessageRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.incidenttracker.features.flowexplorer.job.error.FlowExplorerJobChatUnavailableException;
import pl.mkn.incidenttracker.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerJobServiceTest {

    private final FlowExplorerContextService contextService = mock(FlowExplorerContextService.class);
    private final FlowExplorerPromptPreparationService promptPreparationService =
            mock(FlowExplorerPromptPreparationService.class);
    private final FlowExplorerFollowUpPromptPreparationService followUpPromptPreparationService =
            mock(FlowExplorerFollowUpPromptPreparationService.class);
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler =
            mock(FlowExplorerCopilotRunRequestAssembler.class);
    private final CopilotRunPreparationService runPreparationService = mock(CopilotRunPreparationService.class);
    private final CopilotSdkExecutionGateway executionGateway = mock(CopilotSdkExecutionGateway.class);
    private final FlowExplorerAiResponseParser responseParser = new FlowExplorerAiResponseParser(new ObjectMapper());
    private final TaskExecutor directExecutor = Runnable::run;
    private final FlowExplorerJobService flowExplorerJobService = new FlowExplorerJobService(
            contextService,
            promptPreparationService,
            followUpPromptPreparationService,
            runRequestAssembler,
            runPreparationService,
            executionGateway,
            responseParser,
            directExecutor
    );

    @Test
    void shouldRunAiAndCompleteJobWithParsedResult() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        var usage = usage();
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class)))
                .thenReturn(new CopilotExecutionResult(aiJson(), usage));

        var started = flowExplorerJobService.startJob(request);

        assertNotNull(started.jobId());
        assertEquals("COMPLETED", started.status());
        assertEquals("feature/FLOW-42", started.branch());
        assertEquals(FlowExplorerAnalysisGoal.DEEP_DISCOVERY, started.goal());
        assertEquals(2, started.steps().size());
        assertTrue(started.steps().stream().allMatch(step -> "COMPLETED".equals(step.status())));
        assertNotNull(started.contextSnapshot());
        assertEquals("Flow Explorer canonical prompt", started.preparedPrompt());
        assertEquals(FlowExplorerAnalysisGoal.DEEP_DISCOVERY, started.result().goal());
        assertEquals("Tester chce poznac GET /api/customers/{id}.", started.result().aiResponse().overview().markdown());
        assertEquals("high", started.result().aiResponse().confidence());
        assertEquals(4, started.result().aiResponse().sections().size());
        assertEquals(FlowExplorerResultSectionId.BUSINESS_FLOW_RULES,
                started.result().aiResponse().sections().get(0).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP,
                started.result().aiResponse().sections().get(0).mode());
        assertEquals("Controller przyjmuje request.",
                started.result().aiResponse().sections().get(0).markdown());
        assertSame(usage, started.result().usage());

        var fetched = flowExplorerJobService.getJob(started.jobId());
        assertEquals(started.jobId(), fetched.jobId());
        assertEquals("COMPLETED", fetched.status());
        verify(contextService).buildContext(expectedContextRequest());
        verify(promptPreparationService).prepare(request, contextSnapshot);
        verify(runRequestAssembler).assemble(started.jobId(), request, contextSnapshot, promptPreparation);
        verify(runPreparationService).prepare(runRequest);
    }

    @Test
    void shouldCompleteWithParserFallbackWhenAiReturnsInvalidJson() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class)))
                .thenReturn(new CopilotExecutionResult("not json", usage()));

        var started = flowExplorerJobService.startJob(request);

        assertEquals("COMPLETED", started.status());
        assertEquals("low", started.result().aiResponse().confidence());
        assertEquals(
                "Nie udalo sie sparsowac odpowiedzi AI do kontraktu Flow Explorer.",
                started.result().aiResponse().overview().markdown()
        );
        assertTrue(started.result().aiResponse().globalVisibilityLimits().contains("AI response was not valid JSON."));
        assertFalse(started.result().aiResponse().globalOpenQuestions().isEmpty());
    }

    @Test
    void shouldCaptureToolEvidenceAndAiActivityFromRuntimeSinks() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        var evidence = new AnalysisEvidenceSection(
                "gitlab",
                "file-chunk",
                List.of(new AnalysisEvidenceItem(
                        "CustomerController",
                        List.of(new AnalysisEvidenceAttribute("filePath", "CustomerController.java"))
                ))
        );
        var activity = new AnalysisAiActivityEvent(
                "event-1",
                null,
                "tool-call",
                "tool",
                "completed",
                "GitLab read",
                "Read focused chunk.",
                null,
                null,
                "call-1",
                "gitlab_read_repository_file_chunk",
                Instant.parse("2026-04-12T18:00:00Z"),
                Map.of()
        );
        when(executionGateway.execute(any(CopilotPreparedSession.class))).thenAnswer(invocation -> {
            var session = (CopilotPreparedSession) invocation.getArgument(0);
            session.evidenceSink().accept(evidence);
            session.activitySink().accept(activity);
            return new CopilotExecutionResult(aiJson(), usage());
        });

        var started = flowExplorerJobService.startJob(request);

        assertEquals(1, started.toolEvidenceSections().size());
        assertEquals("gitlab", started.toolEvidenceSections().get(0).provider());
        assertEquals(1, started.aiActivityEvents().size());
        assertEquals("gitlab_read_repository_file_chunk", started.aiActivityEvents().get(0).toolName());
    }

    @Test
    void shouldMarkJobFailedWhenAiExecutionFails() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class)))
                .thenThrow(new IllegalStateException("Copilot unavailable"));

        var started = flowExplorerJobService.startJob(request);

        assertEquals("FAILED", started.status());
        assertEquals("FLOW_EXPLORER_AI_FAILED", started.errorCode());
        assertEquals("Copilot unavailable", started.errorMessage());
        assertNotNull(started.contextSnapshot());
        assertEquals("Flow Explorer canonical prompt", started.preparedPrompt());
        assertEquals("FAILED", started.steps().get(started.steps().size() - 1).status());
    }

    @Test
    void shouldReturnCollectingSnapshotBeforeAsyncExecutionCompletes() {
        var queuedTask = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var service = new FlowExplorerJobService(
                contextService,
                promptPreparationService,
                followUpPromptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                queuedTask::set
        );

        var started = service.startJob(request());

        assertEquals("COLLECTING_CONTEXT", started.status());
        assertEquals("DETERMINISTIC_CONTEXT", started.currentStepCode());
        assertNotNull(queuedTask.get());
    }

    @Test
    void shouldThrowWhenJobIsMissing() {
        assertThrows(
                FlowExplorerJobNotFoundException.class,
                () -> flowExplorerJobService.getJob("missing-job")
        );
    }

    @Test
    void shouldRunFollowUpChatAfterCompletedJob() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        var followUpPromptPreparation = followUpPromptPreparation();
        var followUpRunRequest = followUpRunRequest();
        var followUpPreparedSession = preparedSession(followUpRunRequest);
        var evidence = new AnalysisEvidenceSection(
                "gitlab",
                "follow-up-file-chunk",
                List.of(new AnalysisEvidenceItem(
                        "CustomerService",
                        List.of(new AnalysisEvidenceAttribute("filePath", "CustomerService.java"))
                ))
        );
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        when(followUpPromptPreparationService.prepare(any(FlowExplorerFollowUpChatRequest.class)))
                .thenReturn(followUpPromptPreparation);
        when(runRequestAssembler.assembleFollowUp(
                any(String.class),
                same(request),
                same(contextSnapshot),
                same(followUpPromptPreparation)
        )).thenReturn(new FlowExplorerCopilotRunAssembly(
                followUpRunRequest,
                new CopilotToolSessionContext("follow-up-123", "flow-explorer-follow-up-123", Map.of()),
                FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of())
        ));
        when(runPreparationService.prepare(followUpRunRequest)).thenReturn(followUpPreparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class))).thenAnswer(invocation -> {
            var session = (CopilotPreparedSession) invocation.getArgument(0);
            if ("Flow Explorer canonical prompt".equals(session.prompt())) {
                return new CopilotExecutionResult(aiJson(), usage());
            }
            session.evidenceSink().accept(evidence);
            return new CopilotExecutionResult("Walidacja jest w CustomerService.validate.", null);
        });

        var started = flowExplorerJobService.startJob(request);
        var afterChatStart = flowExplorerJobService.startChatMessage(
                started.jobId(),
                new FlowExplorerChatMessageRequest("Gdzie jest walidacja?")
        );

        assertEquals("COMPLETED", afterChatStart.status());
        assertEquals(2, afterChatStart.chatMessages().size());
        assertEquals("USER", afterChatStart.chatMessages().get(0).role());
        assertEquals("Gdzie jest walidacja?", afterChatStart.chatMessages().get(0).content());
        assertEquals("ASSISTANT", afterChatStart.chatMessages().get(1).role());
        assertEquals("COMPLETED", afterChatStart.chatMessages().get(1).status());
        assertEquals("Walidacja jest w CustomerService.validate.", afterChatStart.chatMessages().get(1).content());
        assertEquals("Flow Explorer follow-up prompt", afterChatStart.chatMessages().get(1).prompt());
        assertEquals(1, afterChatStart.chatMessages().get(1).toolEvidenceSections().size());
        assertEquals("gitlab", afterChatStart.chatMessages().get(1).toolEvidenceSections().get(0).provider());

        verify(followUpPromptPreparationService).prepare(any(FlowExplorerFollowUpChatRequest.class));
        verify(runRequestAssembler).assembleFollowUp(
                any(String.class),
                same(request),
                same(contextSnapshot),
                same(followUpPromptPreparation)
        );
    }

    @Test
    void shouldRejectFollowUpChatBeforeJobIsCompleted() {
        var queuedTask = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var service = new FlowExplorerJobService(
                contextService,
                promptPreparationService,
                followUpPromptPreparationService,
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                queuedTask::set
        );
        var started = service.startJob(request());

        var exception = assertThrows(
                FlowExplorerJobChatUnavailableException.class,
                () -> service.startChatMessage(
                        started.jobId(),
                        new FlowExplorerChatMessageRequest("Czy endpoint zapisuje dane?")
                )
        );

        assertEquals("FLOW_EXPLORER_CHAT_NOT_READY", exception.code());
    }

    private void givenContextPromptAndRun(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation promptPreparation,
            CopilotRunRequest runRequest,
            CopilotPreparedSession preparedSession
    ) {
        when(contextService.buildContext(expectedContextRequest())).thenReturn(contextSnapshot);
        when(promptPreparationService.prepare(request, contextSnapshot)).thenReturn(promptPreparation);
        when(runRequestAssembler.assemble(any(String.class), same(request), same(contextSnapshot), same(promptPreparation)))
                .thenReturn(new FlowExplorerCopilotRunAssembly(
                        runRequest,
                        new CopilotToolSessionContext("job-123", "flow-explorer-job-123", Map.of()),
                        FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of())
                ));
        when(runPreparationService.prepare(runRequest)).thenReturn(preparedSession);
    }

    private static FlowExplorerContextRequest expectedContextRequest() {
        return new FlowExplorerContextRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW_RULES)
        );
    }

    private static FlowExplorerJobStartRequest request() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW_RULES),
                "Skup sie na jezyku zrozumialym dla testera.",
                "gpt-5.4",
                "medium"
        );
    }

    private static FlowExplorerPromptPreparation promptPreparation() {
        return new FlowExplorerPromptPreparation(
                "Flow Explorer canonical prompt",
                List.of(),
                Map.of("flow-explorer/context-snapshot.json", "{\"systemId\":\"crm-service\"}")
        );
    }

    private static FlowExplorerPromptPreparation followUpPromptPreparation() {
        return new FlowExplorerPromptPreparation(
                "Flow Explorer follow-up prompt",
                List.of(),
                Map.of("flow-explorer/initial-result.md", "initial result")
        );
    }

    private static CopilotRunRequest runRequest() {
        return new CopilotRunRequest(
                "job-123",
                "Flow Explorer canonical prompt",
                new CopilotSessionConfigRequest(
                        "flow-explorer-job-123",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "Denied"
                ),
                Map.of("flow-explorer/context-snapshot.json", "{\"systemId\":\"crm-service\"}"),
                null
        );
    }

    private static CopilotRunRequest followUpRunRequest() {
        return new CopilotRunRequest(
                "follow-up-123",
                "Flow Explorer follow-up prompt",
                new CopilotSessionConfigRequest(
                        "flow-explorer-follow-up-123",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "Denied"
                ),
                Map.of("flow-explorer/initial-result.md", "initial result"),
                null
        );
    }

    private static CopilotPreparedSession preparedSession(CopilotRunRequest runRequest) {
        return new CopilotPreparedSession(
                runRequest.runReference(),
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt(runRequest.prompt()),
                runRequest.prompt(),
                runRequest.artifactContents()
        );
    }

    private static AnalysisAiUsage usage() {
        return new AnalysisAiUsage(
                100,
                80,
                0,
                0,
                180,
                0.12,
                1200,
                1,
                "gpt-5.4",
                null,
                null,
                null
        );
    }

    private static String aiJson() {
        return """
                {
                  "goal": "DEEP_DISCOVERY",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Tester chce poznac GET /api/customers/{id}.",
                    "confidence": "high",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "compact",
                      "markdown": "Controller przyjmuje request.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "compact",
                      "markdown": "id jest wymagane.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "PERSISTENCE",
                      "title": "Persistence",
                      "mode": "compact",
                      "markdown": "Repository pobiera klienta po id.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "compact",
                      "markdown": "Brak potwierdzonych integracji downstream.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                  "confidence": "high"
                }
                """;
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
