package pl.mkn.tdw.features.flowexplorer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponseParser;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunAssembly;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotToolAccessPolicy;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportMapper;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextRequest;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextService;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerChatMessageRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobChatUnavailableException;
import pl.mkn.tdw.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
import pl.mkn.tdw.features.flowexplorer.job.localworkspace.FlowExplorerLocalRunPersistence;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerJobServiceTest {

    private final FlowExplorerContextService contextService = mock(FlowExplorerContextService.class);
    private final FlowExplorerPromptPreparationService promptPreparationService =
            mock(FlowExplorerPromptPreparationService.class);
    private final FlowExplorerCopilotRunRequestAssembler runRequestAssembler =
            mock(FlowExplorerCopilotRunRequestAssembler.class);
    private final CopilotRunPreparationService runPreparationService = mock(CopilotRunPreparationService.class);
    private final CopilotSdkExecutionGateway executionGateway = mock(CopilotSdkExecutionGateway.class);
    private final FlowExplorerAiResponseParser responseParser = new FlowExplorerAiResponseParser(new ObjectMapper());
    private final TaskExecutor directExecutor = Runnable::run;
    private final FlowExplorerJobService flowExplorerJobService = FlowExplorerJobServiceTestCreator.create(
            contextService,
            promptPreparationService,
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
        assertEquals(FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                started.result().aiResponse().sections().get(0).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP,
                started.result().aiResponse().sections().get(0).mode());
        assertEquals(functionalFlowMarkdown(),
                started.result().aiResponse().sections().get(0).markdown());
        assertSame(usage, started.result().usage());

        var fetched = flowExplorerJobService.getJob(started.jobId());
        assertEquals(started.jobId(), fetched.jobId());
        assertEquals("COMPLETED", fetched.status());
        verify(contextService).buildContext(expectedContextRequest());
        verify(promptPreparationService).prepare(request, contextSnapshot);
        verify(runRequestAssembler).assemble(
                eq(started.jobId()),
                same(request),
                same(contextSnapshot),
                same(promptPreparation),
                any(AnalysisAiAuthRef.class)
        );
        verify(runPreparationService).prepare(runRequest);
    }

    @Test
    void shouldCompleteJobFromReportSnapshotBeforeAssistantJsonFallback() {
        var request = request();
        var contextSnapshot = contextSnapshot();
        var promptPreparation = promptPreparation();
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        var usage = usage();
        givenContextPromptAndRun(request, contextSnapshot, promptPreparation, runRequest, preparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class)))
                .thenReturn(new CopilotExecutionResult("Report saved.", usage, "initial-session-1", report()));

        var started = flowExplorerJobService.startJob(request);

        assertEquals("COMPLETED", started.status());
        assertEquals("Tester chce poznac endpoint z raportu.", started.result().aiResponse().overview().markdown());
        assertEquals("high", started.result().aiResponse().confidence());
        assertEquals(4, started.result().aiResponse().sections().size());
        assertEquals("Flow zapisany przez report tool.", started.result().aiResponse().sections().get(0).markdown());
        assertEquals(List.of("crm-service:CustomerController.java:L12-L24"),
                started.result().aiResponse().sourceReferences());
        assertNotNull(started.report());
        assertEquals("report-1", started.report().reportId());
        assertEquals("Flow Explorer: GET /api/customers/{id}", started.report().header());
        assertSame(usage, started.result().usage());
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
        var service = FlowExplorerJobServiceTestCreator.create(
                contextService,
                promptPreparationService,
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
    void shouldPersistCollectingSnapshotWhenJobStarts() {
        var queuedTask = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var localRunPersistence = mock(FlowExplorerLocalRunPersistence.class);
        var service = FlowExplorerJobServiceTestCreator.create(
                contextService,
                promptPreparationService,
                new FlowExplorerFollowUpPromptPreparationService(),
                runRequestAssembler,
                runPreparationService,
                executionGateway,
                responseParser,
                new FlowExplorerReportMapper(),
                queuedTask::set,
                () -> AnalysisAiAuthRef.localToken(null),
                new CopilotRunAuthMapper(),
                auth -> new CopilotAccessToken("test-token", null, null, false),
                localRunPersistence
        );

        var started = service.startJob(request());

        assertEquals("COLLECTING_CONTEXT", started.status());
        verify(localRunPersistence).persistRunSnapshot(
                argThat(snapshot -> snapshot != null
                        && started.jobId().equals(snapshot.jobId())
                        && "COLLECTING_CONTEXT".equals(snapshot.status())),
                isNull(),
                isNull()
        );
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
        when(runRequestAssembler.assembleFollowUp(
                any(String.class),
                same(request),
                same(contextSnapshot),
                any(FlowExplorerPromptPreparation.class),
                eq("initial-session-1"),
                any(AnalysisAiAuthRef.class)
        )).thenReturn(new FlowExplorerCopilotRunAssembly(
                followUpRunRequest,
                new CopilotToolSessionContext("follow-up-123", "initial-session-1", Map.of()),
                FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of())
        ));
        when(runPreparationService.prepare(followUpRunRequest)).thenReturn(followUpPreparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class))).thenAnswer(invocation -> {
            var session = (CopilotPreparedSession) invocation.getArgument(0);
            if ("Flow Explorer canonical prompt".equals(session.prompt())) {
                return new CopilotExecutionResult(aiJson(), usage(), "initial-session-1");
            }
            session.evidenceSink().accept(evidence);
            return new CopilotExecutionResult("Walidacja jest w CustomerService.validate.", null, "follow-up-session-1");
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
        assertTrue(afterChatStart.chatMessages().get(1).prompt().contains("# Flow Explorer follow-up chat"));
        assertTrue(afterChatStart.chatMessages().get(1).prompt().contains("Domyslnie odpowiedz w Markdown"));
        assertTrue(afterChatStart.chatMessages().get(1).prompt().contains("Nie zwracaj pelnego JSON"));
        assertTrue(afterChatStart.chatMessages().get(1).prompt().contains("Gdzie jest walidacja?"));
        assertEquals(1, afterChatStart.chatMessages().get(1).toolEvidenceSections().size());
        assertEquals("gitlab", afterChatStart.chatMessages().get(1).toolEvidenceSections().get(0).provider());

        var promptCaptor = ArgumentCaptor.forClass(FlowExplorerPromptPreparation.class);
        verify(runRequestAssembler).assembleFollowUp(
                any(String.class),
                same(request),
                same(contextSnapshot),
                promptCaptor.capture(),
                eq("initial-session-1"),
                any(AnalysisAiAuthRef.class)
        );
        assertTrue(promptCaptor.getValue().prompt().contains("# Flow Explorer follow-up chat"));
        assertTrue(promptCaptor.getValue().prompt().contains("Nie zakladaj, ze initial analysis przeczytala cala implementacje"));
        assertTrue(promptCaptor.getValue().prompt().contains("domyslnie uzyj dostepnych Flow Explorer tools"));
        assertTrue(promptCaptor.getValue().prompt().contains("Docelowy odbiorca to analityk albo tester"));
        assertTrue(promptCaptor.getValue().prompt().contains("Gdzie jest walidacja?"));
        assertTrue(promptCaptor.getValue().artifacts().isEmpty());
        assertTrue(promptCaptor.getValue().artifactContents().isEmpty());
    }

    @Test
    void shouldRejectFollowUpChatBeforeJobIsCompleted() {
        var queuedTask = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var service = FlowExplorerJobServiceTestCreator.create(
                contextService,
                promptPreparationService,
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
        when(runRequestAssembler.assemble(
                any(String.class),
                same(request),
                same(contextSnapshot),
                same(promptPreparation),
                any(AnalysisAiAuthRef.class)
        ))
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
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW)
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
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                null,
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
                "Gdzie jest walidacja?",
                new CopilotSessionConfigRequest(
                        "flow-explorer-follow-up-123",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "Denied"
                ),
                Map.of(),
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
                      "id": "FUNCTIONAL_FLOW",
                      "title": "Functional flow",
                      "mode": "deep",
                      "markdown": "%s",
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
                """.formatted(functionalFlowMarkdownJson());
    }

    private static AnalysisReport report() {
        return new AnalysisReport(
                "report-1",
                "Flow Explorer: GET /api/customers/{id}",
                "crm-service | feature/FLOW-42 | DEEP_DISCOVERY",
                "",
                List.of(
                        new AnalysisReportSection(
                                "OVERVIEW",
                                "Overview",
                                0,
                                "Tester chce poznac endpoint z raportu.",
                                reportMeta("high")
                        ),
                        reportSection(FlowExplorerResultSectionId.FUNCTIONAL_FLOW, "Flow zapisany przez report tool."),
                        reportSection(FlowExplorerResultSectionId.VALIDATIONS, "Walidacje zapisane przez report tool."),
                        reportSection(FlowExplorerResultSectionId.PERSISTENCE, "Persistence zapisany przez report tool."),
                        reportSection(FlowExplorerResultSectionId.INTEGRATIONS, "Integracje zapisane przez report tool.")
                ),
                reportMeta("high")
        );
    }

    private static AnalysisReportSection reportSection(
            FlowExplorerResultSectionId sectionId,
            String markdown
    ) {
        return new AnalysisReportSection(
                sectionId.name(),
                sectionId.title(),
                sectionId.ordinal() + 1,
                markdown,
                AnalysisReportMeta.empty()
        );
    }

    private static AnalysisReportMeta reportMeta(String confidence) {
        return new AnalysisReportMeta(
                List.of(new AnalysisReportReference(
                        "code",
                        "CustomerController",
                        "crm-service:CustomerController.java:L12-L24",
                        "Endpoint handler"
                )),
                List.of(),
                List.of(),
                List.of(),
                confidence,
                List.of()
        );
    }

    private static String functionalFlowMarkdownJson() {
        return functionalFlowMarkdown()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private static String functionalFlowMarkdown() {
        return String.join("\n", List.of(
                "- **Cel funkcjonalny:** pokazac profil klienta CRM w procesie obslugi klienta.",
                "- **Flow krok po kroku:** 1. request przechodzi przez auth/authz; 2. system waliduje id klienta; 3. dociaga profil klienta; 4. sprawdza status widocznosci; 5. bez zapisu stanu zwraca profil CRM.",
                "- **Koordynacja i routing:** sciezka zalezy od identyfikatora klienta oraz statusu profilu odczytanego z danych CRM.",
                "- **Kalkulacje i reguly funkcjonalne:** system klasyfikuje profil jako widoczny tylko wtedy, gdy klient istnieje i status pozwala pokazac dane operatorowi.",
                "- **Rozgalezienia zalezne od kontekstu:** brak klienta konczy flow kontrolowanym brakiem rekordu, a nieaktywny status wymaga potwierdzenia oczekiwanego zachowania.",
                "- **Handoffy i efekty uboczne:** endpoint tylko odczytuje dane i zwraca odpowiedz; szczegoly persistence zostaja w sekcji PERSISTENCE.",
                "- **Akcent goal:** w DEEP_DISCOVERY wskazac glowne warianty funkcjonalne i znaczenie dla procesu."
        ));
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
