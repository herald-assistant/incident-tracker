package pl.mkn.tdw.features.incidentanalysis.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.integrations.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.tdw.integrations.elasticsearch.TestElasticLogPort;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.source.GitLabSourceResolveService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextAdapter;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;
import pl.mkn.tdw.features.incidentanalysis.testsupport.TestOperationalContextProjectPathResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedbackEvidenceMapper;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisOrchestrator;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisChatMessageRequest;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStartRequest;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobChatUnavailableException;
import pl.mkn.tdw.features.incidentanalysis.job.localworkspace.IncidentAnalysisLocalRunPersistence;
import pl.mkn.tdw.features.incidentanalysis.testsupport.TestInitialAnalysisProvider;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalysisJobServiceTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();
    private final CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
    private final AnalysisJobService analysisJobService = analysisJobService(
            new TestInitialAnalysisProvider(),
            new TestAnalysisChatProvider(),
            taskExecutor
    );

    @Test
    void shouldReturnQueuedJobThenCompleteItAfterWorkerRuns() {
        var started = analysisJobService.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));

        assertNotNull(started.analysisId());
        assertEquals("timeout-123", started.correlationId());
        assertNull(started.aiModel());
        assertNull(started.reasoningEffort());
        assertEquals("QUEUED", started.status());
        assertEquals(6, started.steps().size());
        assertEquals("PENDING", started.steps().get(0).status());
        assertFalse(taskExecutor.isEmpty());

        taskExecutor.runNext();

        var completed = analysisJobService.getAnalysis(started.analysisId());

        assertEquals("COMPLETED", completed.status());
        assertEquals("dev3", completed.environment());
        assertEquals("dev/atlas", completed.gitLabBranch());
        assertEquals(3, completed.evidenceSections().size());
        assertEquals(0, completed.toolEvidenceSections().size());
        assertEquals(
                "Synthetic AI prompt for correlationId=timeout-123, environment=dev3, gitLabBranch=dev/atlas",
                completed.preparedPrompt()
        );
        assertNotNull(completed.result());
        assertNotNull(completed.report());
        assertEquals("test-report-timeout-123", completed.report().reportId());
        assertEquals("DOWNSTREAM_TIMEOUT", completed.report().header());
        assertEquals("DOWNSTREAM_TIMEOUT", completed.result().detectedProblem());
        assertEquals(
                "Analiza funkcjonalna: incydent dotyka procesu katalogowego, ktory pobiera dane katalogowe przed zbudowaniem odpowiedzi.",
                completed.result().functionalAnalysis()
        );
        assertEquals("Catalog profile lookup", completed.result().affectedProcess());
        assertEquals("Catalog Context", completed.result().affectedBoundedContext());
        assertEquals("Core Integration Team", completed.result().affectedTeam());
        assertEquals("COMPLETED", completed.steps().get(5).status());
    }

    @Test
    void shouldPersistCompletedInitialAnalysisRun() {
        var persistence = new CapturingLocalRunPersistence();
        var persistenceTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new SessionAwareInitialAnalysisProvider("copilot-session-1"),
                new TestAnalysisChatProvider(),
                persistenceTaskExecutor,
                persistence
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        persistenceTaskExecutor.runNext();

        assertEquals(1, persistence.snapshots.size());
        assertEquals(started.analysisId(), persistence.snapshots.get(0).analysisId());
        assertEquals("COMPLETED", persistence.snapshots.get(0).status());
        assertNotNull(persistence.snapshots.get(0).report());
        assertEquals("session-aware-report-timeout-123", persistence.snapshots.get(0).report().reportId());
        assertEquals("timeout-123", persistence.requests.get(0).correlationId());
        assertEquals("CRM/runtime", persistence.requests.get(0).gitLabGroup());
        assertEquals("copilot-session-1", persistence.copilotSessionIds.get(0));
    }

    @Test
    void shouldNotPersistFailedInitialAnalysisRun() {
        var persistence = new CapturingLocalRunPersistence();
        var failingTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new FailingInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                failingTaskExecutor,
                persistence
        );

        service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        failingTaskExecutor.runNext();

        assertTrue(persistence.snapshots.isEmpty());
        assertTrue(persistence.requests.isEmpty());
    }

    @Test
    void shouldPassSelectedAiOptionsToAnalysisFlow() {
        var provider = new CapturingOptionsInitialAnalysisProvider();
        var optionsTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(provider, new TestAnalysisChatProvider(), optionsTaskExecutor);

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", "gpt-5.4", "high"));

        assertEquals("gpt-5.4", started.aiModel());
        assertEquals("high", started.reasoningEffort());

        optionsTaskExecutor.runNext();

        assertEquals("gpt-5.4", provider.lastPreparedRequest.options().model());
        assertEquals("high", provider.lastPreparedRequest.options().reasoningEffort());
        var completed = service.getAnalysis(started.analysisId());
        assertEquals("gpt-5.4", completed.aiModel());
        assertEquals("high", completed.reasoningEffort());
    }

    @Test
    void shouldResolveCopilotTokenBeforeCreatingJob() {
        var provider = new CapturingOptionsInitialAnalysisProvider();
        var authRef = AnalysisAiAuthRef.githubApp("operator-session-1", "octocat");
        var tokenResolver = new CapturingAccessTokenResolver("ghu_secret_operator_token");
        var authTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                provider,
                new TestAnalysisChatProvider(),
                authTaskExecutor,
                () -> authRef,
                tokenResolver
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));

        assertEquals("operator-session-1", tokenResolver.lastAuth.principalId());
        assertEquals("octocat", tokenResolver.lastAuth.githubLogin());
        assertFalse(started.toString().contains("ghu_secret_operator_token"));

        authTaskExecutor.runNext();

        assertEquals(authRef, provider.lastPreparedRequest.authRef());
        assertFalse(service.getAnalysis(started.analysisId()).toString().contains("ghu_secret_operator_token"));
    }

    @Test
    void shouldRejectJobBeforeQueueingWhenCopilotAuthIsMissing() {
        var authTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new TestInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                authTaskExecutor,
                () -> {
                    throw new GitHubCopilotAuthRequiredException();
                },
                auth -> new CopilotAccessToken("unused", null, null, false)
        );

        assertThrows(
                GitHubCopilotAuthRequiredException.class,
                () -> service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null))
        );
        assertTrue(authTaskExecutor.isEmpty());
    }

    @Test
    void shouldExposeAiTokenUsageOnFinalAiStep() {
        var usageTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new UsageAwareInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                usageTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        usageTaskExecutor.runNext();

        var completed = service.getAnalysis(started.analysisId());
        var aiStep = completed.steps().get(5);

        assertNotNull(completed.result().usage());
        assertEquals(1700L, completed.result().usage().totalTokens());
        assertNotNull(aiStep.usage());
        assertEquals(1700L, aiStep.usage().totalTokens());
        assertEquals(1200L, aiStep.usage().inputTokens());
        assertEquals(500L, aiStep.usage().outputTokens());
        assertEquals("gpt-5.4", aiStep.usage().model());
    }

    @Test
    void shouldMarkJobAsNotFoundWhenEvidenceIsMissing() {
        var started = analysisJobService.startAnalysis(new AnalysisJobStartRequest("not-found", null, null));

        assertEquals("QUEUED", started.status());
        taskExecutor.runNext();

        var finished = analysisJobService.getAnalysis(started.analysisId());

        assertEquals("NOT_FOUND", finished.status());
        assertEquals("ANALYSIS_DATA_NOT_FOUND", finished.errorCode());
        assertNull(finished.result());
        assertEquals("SKIPPED", finished.steps().get(5).status());
    }

    @Test
    void shouldKeepPreparedPromptWhenAiAnalysisFails() {
        var failingTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new FailingInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                failingTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));

        assertEquals("QUEUED", started.status());
        failingTaskExecutor.runNext();

        var finished = service.getAnalysis(started.analysisId());

        assertEquals("FAILED", finished.status());
        assertEquals("ANALYSIS_FAILED", finished.errorCode());
        assertEquals("AI gateway timeout", finished.errorMessage());
        assertEquals(0, finished.toolEvidenceSections().size());
        assertEquals(
                "Prepared prompt for external fallback correlationId=timeout-123",
                finished.preparedPrompt()
        );
        assertNull(finished.result());
        assertEquals("FAILED", finished.steps().get(5).status());
    }

    @Test
    void shouldExposeAiToolFetchedGitLabFilesDuringPollingWhileAiStepIsRunning() throws Exception {
        var toolAwareProvider = new BlockingToolAwareInitialAnalysisProvider();
        var blockingTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                toolAwareProvider,
                new TestAnalysisChatProvider(),
                blockingTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        var worker = new Thread(blockingTaskExecutor::runNext);
        worker.start();

        assertTrue(toolAwareProvider.awaitToolEvidencePublication());

        var inProgress = service.getAnalysis(started.analysisId());

        assertEquals("ANALYZING", inProgress.status());
        assertEquals("AI_ANALYSIS", inProgress.currentStepCode());
        assertEquals(1, inProgress.toolEvidenceSections().size());
        assertEquals("gitlab", inProgress.toolEvidenceSections().get(0).provider());
        assertEquals("tool-fetched-code", inProgress.toolEvidenceSections().get(0).category());
        assertEquals(1, inProgress.toolEvidenceSections().get(0).items().size());
        assertEquals(
                "Sprawdzam fragment klienta z timeoutem.",
                inProgress.toolEvidenceSections().get(0).items().get(0).attributes().stream()
                        .filter(attribute -> "reason".equals(attribute.name()))
                        .findFirst()
                        .orElseThrow()
                        .value()
        );

        toolAwareProvider.finish();
        worker.join(2_000L);

        var completed = service.getAnalysis(started.analysisId());
        assertEquals("COMPLETED", completed.status());
        assertEquals(1, completed.toolEvidenceSections().size());
        assertEquals(1, completed.toolEvidenceSections().get(0).items().size());
    }

    @Test
    void shouldExposeInitialToolFeedbackInJobSnapshot() {
        var feedbackTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new FeedbackAwareInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                feedbackTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        feedbackTaskExecutor.runNext();

        var completed = service.getAnalysis(started.analysisId());

        assertEquals("COMPLETED", completed.status());
        assertEquals(1, completed.toolFeedback().size());
        assertEquals("gitlab_find_flow_context", completed.toolFeedback().get(0).targetToolName());
        assertEquals("partial", completed.toolFeedback().get(0).usefulness());
    }

    @Test
    void shouldRunFollowUpChatAfterCompletedAnalysis() {
        var chatProvider = new ToolAwareTestAnalysisChatProvider();
        var chatTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(new TestInitialAnalysisProvider(), chatProvider, chatTaskExecutor);

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        chatTaskExecutor.runNext();

        var afterChatStart = service.startChatMessage(
                started.analysisId(),
                new AnalysisChatMessageRequest("Potwierdz w repo gdzie ustawiany jest timeout.")
        );

        assertEquals(2, afterChatStart.chatMessages().size());
        assertEquals("USER", afterChatStart.chatMessages().get(0).role());
        assertEquals("ASSISTANT", afterChatStart.chatMessages().get(1).role());
        assertEquals("IN_PROGRESS", afterChatStart.chatMessages().get(1).status());
        assertFalse(chatTaskExecutor.isEmpty());

        chatTaskExecutor.runNext();

        var completed = service.getAnalysis(started.analysisId());
        var assistantMessage = completed.chatMessages().get(1);
        assertEquals("COMPLETED", assistantMessage.status());
        assertEquals(
                "Potwierdzilem w repo, ze timeout jest ustawiany w kliencie katalogu.",
                assistantMessage.content()
        );
        assertEquals("Synthetic follow-up prompt for timeout-123", assistantMessage.prompt());
        assertEquals(1, assistantMessage.toolEvidenceSections().size());
        assertEquals("gitlab", assistantMessage.toolEvidenceSections().get(0).provider());
    }

    @Test
    void shouldRejectFollowUpChatWhenCompletedAnalysisHasNoCopilotSessionId() {
        var chatTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                new CapturingOptionsInitialAnalysisProvider(),
                new TestAnalysisChatProvider(),
                chatTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        chatTaskExecutor.runNext();

        var exception = assertThrows(
                AnalysisJobChatUnavailableException.class,
                () -> service.startChatMessage(
                        started.analysisId(),
                        new AnalysisChatMessageRequest("Potwierdz w repo gdzie ustawiany jest timeout.")
                )
        );

        assertEquals("ANALYSIS_CHAT_NOT_CONTINUABLE", exception.code());
        assertTrue(chatTaskExecutor.isEmpty());
    }

    @Test
    void shouldAttachToolFeedbackToFollowUpAssistantMessage() {
        var chatProvider = new FeedbackAwareTestAnalysisChatProvider();
        var chatTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(new TestInitialAnalysisProvider(), chatProvider, chatTaskExecutor);

        var started = service.startAnalysis(new AnalysisJobStartRequest("timeout-123", null, null));
        chatTaskExecutor.runNext();
        service.startChatMessage(
                started.analysisId(),
                new AnalysisChatMessageRequest("Czy tool zwrocil wystarczajacy wynik?")
        );
        chatTaskExecutor.runNext();

        var completed = service.getAnalysis(started.analysisId());
        var assistantMessage = completed.chatMessages().get(1);

        assertEquals("COMPLETED", assistantMessage.status());
        assertEquals(0, completed.toolFeedback().size());
        assertEquals(1, assistantMessage.toolFeedback().size());
        assertEquals("db_find_tables", assistantMessage.toolFeedback().get(0).targetToolName());
        assertEquals("adapter_result", assistantMessage.toolFeedback().get(0).improvementArea());
    }

    private AnalysisJobService analysisJobService(
            InitialAnalysisProvider initialAnalysisProvider,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor taskExecutor
    ) {
        return analysisJobService(
                initialAnalysisProvider,
                analysisAiChatProvider,
                taskExecutor,
                IncidentAnalysisLocalRunPersistence.NO_OP
        );
    }

    private AnalysisJobService analysisJobService(
            InitialAnalysisProvider initialAnalysisProvider,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor taskExecutor,
            IncidentAnalysisLocalRunPersistence localRunPersistence
    ) {
        return analysisJobService(
                initialAnalysisProvider,
                analysisAiChatProvider,
                taskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                auth -> new CopilotAccessToken("test-token", null, null, false),
                localRunPersistence
        );
    }

    private AnalysisJobService analysisJobService(
            InitialAnalysisProvider initialAnalysisProvider,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor taskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotAccessTokenResolver accessTokenResolver
    ) {
        return analysisJobService(
                initialAnalysisProvider,
                analysisAiChatProvider,
                taskExecutor,
                authRefResolver,
                accessTokenResolver,
                IncidentAnalysisLocalRunPersistence.NO_OP
        );
    }

    private AnalysisJobService analysisJobService(
            InitialAnalysisProvider initialAnalysisProvider,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor taskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotAccessTokenResolver accessTokenResolver,
            IncidentAnalysisLocalRunPersistence localRunPersistence
    ) {
        return new AnalysisJobService(
                new AnalysisOrchestrator(
                        new AnalysisEvidenceCollector(
                                new ElasticLogEvidenceProvider(new TestElasticLogPort()),
                                new DeploymentContextEvidenceProvider(deploymentContextResolver),
                                new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
                                new GitLabDeterministicEvidenceProvider(
                                        mock(GitLabRepositoryPort.class),
                                        gitLabProperties,
                                        mock(GitLabSourceResolveService.class),
                                        deploymentContextResolver,
                                        TestOperationalContextProjectPathResolver.empty()
                                ),
                                disabledOperationalContextEvidenceProvider(),
                                directTaskExecutor()
                        ),
                        initialAnalysisProvider,
                        gitLabProperties
                ),
                analysisAiChatProvider,
                taskExecutor,
                authRefResolver,
                new CopilotRunAuthMapper(),
                accessTokenResolver,
                localRunPersistence
        );
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("CRM/runtime");
        return properties;
    }

    private static OperationalContextEvidenceProvider disabledOperationalContextEvidenceProvider() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(false);
        return new OperationalContextEvidenceProvider(
                properties,
                new OperationalContextAdapter(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );
    }

    private static TaskExecutor directTaskExecutor() {
        return Runnable::run;
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            var task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }

        private boolean isEmpty() {
            return tasks.isEmpty();
        }
    }

    private static final class CapturingLocalRunPersistence implements IncidentAnalysisLocalRunPersistence {

        private final List<pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot> snapshots =
                new java.util.ArrayList<>();
        private final List<InitialAnalysisRequest> requests = new java.util.ArrayList<>();
        private final List<String> copilotSessionIds = new java.util.ArrayList<>();

        @Override
        public void persistCompletedInitialRun(
                pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot snapshot,
                InitialAnalysisRequest aiRequest,
                String copilotSessionId
        ) {
            snapshots.add(snapshot);
            requests.add(aiRequest);
            copilotSessionIds.add(copilotSessionId);
        }
    }

    private static final class SessionAwareInitialAnalysisProvider implements InitialAnalysisProvider {

        private final String copilotSessionId;

        private SessionAwareInitialAnalysisProvider(String copilotSessionId) {
            this.copilotSessionId = copilotSessionId;
        }

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "session-aware-ai-provider",
                    request.correlationId(),
                    "Prepared prompt for session capture correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            return new InitialAnalysisResponse(
                    "session-aware-ai-provider",
                    "DOWNSTREAM_TIMEOUT",
                    "Catalog profile lookup",
                    "Catalog Context",
                    "Core Integration Team",
                    "Analiza funkcjonalna: timeout dotyka pobrania katalogu w procesie katalogowym.",
                    "Analiza techniczna: session id Copilota zostal przekazany do lokalnej persystencji.",
                    "medium",
                    List.of(),
                    prepared.prompt(),
                    null,
                    copilotSessionId,
                    report(
                            "session-aware-report-" + prepared.request().correlationId(),
                            "DOWNSTREAM_TIMEOUT",
                            "Analiza funkcjonalna: timeout dotyka pobrania katalogu w procesie katalogowym.",
                            "Analiza techniczna: session id Copilota zostal przekazany do lokalnej persystencji."
                    )
            );
        }
    }

    private static AnalysisReport report(
            String reportId,
            String header,
            String functionalAnalysis,
            String technicalAnalysis
    ) {
        return new AnalysisReport(
                reportId,
                header,
                "Incident test report",
                "",
                List.of(
                        new AnalysisReportSection(
                                "FUNCTIONAL_ANALYSIS",
                                "Functional analysis",
                                1,
                                functionalAnalysis,
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                "TECHNICAL_HANDOFF",
                                "Technical handoff",
                                2,
                                technicalAnalysis,
                                AnalysisReportMeta.empty()
                        )
                ),
                new AnalysisReportMeta(
                        List.of(
                                new AnalysisReportReference("process", "Catalog profile lookup", null, null),
                                new AnalysisReportReference("boundedContext", "Catalog Context", null, null),
                                new AnalysisReportReference("team", "Core Integration Team", null, null)
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        "medium",
                        List.of()
                )
        );
    }

    private static final class FailingInitialAnalysisProvider implements InitialAnalysisProvider {

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "failing-ai-provider",
                    request.correlationId(),
                    "Prepared prompt for external fallback correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            throw new IllegalStateException("AI gateway timeout");
        }
    }

    private static final class CapturingAccessTokenResolver implements CopilotAccessTokenResolver {

        private final String token;
        private CopilotRunAuth lastAuth;

        private CapturingAccessTokenResolver(String token) {
            this.token = token;
        }

        @Override
        public CopilotAccessToken resolve(CopilotRunAuth auth) {
            lastAuth = auth;
            return new CopilotAccessToken(token, auth.githubLogin(), null, auth.userBilling());
        }
    }

    private static final class UsageAwareInitialAnalysisProvider implements InitialAnalysisProvider {

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "usage-aware-ai-provider",
                    request.correlationId(),
                    "Prepared prompt with token usage correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            return new InitialAnalysisResponse(
                    "usage-aware-ai-provider",
                    "DOWNSTREAM_TIMEOUT",
                    "Catalog profile lookup",
                    "Catalog Context",
                    "Core Integration Team",
                    "Analiza funkcjonalna: timeout dotyka pobrania katalogu w procesie katalogowym.",
                    "Analiza techniczna: sprawdz konfiguracje klienta HTTP i latency downstream.",
                    "medium",
                    List.of(),
                    prepared.prompt(),
                    new AnalysisAiUsage(
                            1200L,
                            500L,
                            300L,
                            40L,
                            1700L,
                            1.25D,
                            900L,
                            2,
                            "gpt-5.4",
                            128000L,
                            6400L,
                            7L
                    )
            );
        }
    }

    private static final class CapturingOptionsInitialAnalysisProvider implements InitialAnalysisProvider {

        private InitialAnalysisRequest lastPreparedRequest;

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            lastPreparedRequest = request;
            return new TestPreparedAnalysis(
                    "capturing-options-ai-provider",
                    request.correlationId(),
                    "Prepared prompt for model=%s effort=%s".formatted(
                            request.options().model(),
                            request.options().reasoningEffort()
                    ),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            var options = prepared.request().options() != null
                    ? prepared.request().options()
                    : AnalysisAiOptions.DEFAULT;
            return new InitialAnalysisResponse(
                    "capturing-options-ai-provider",
                    "OPTIONS_CAPTURED",
                    "Options process",
                    "Options Context",
                    "Options Team",
                    "Analiza funkcjonalna: request niesie wybrane opcje runtime AI.",
                    "Analiza techniczna: model " + options.model() + " zostal przekazany do providera.",
                    "high",
                    List.of(),
                    prepared.prompt()
            );
        }
    }

    private static final class BlockingToolAwareInitialAnalysisProvider implements InitialAnalysisProvider {

        private final CountDownLatch toolEvidencePublished = new CountDownLatch(1);
        private final CountDownLatch finishSignal = new CountDownLatch(1);

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "blocking-tool-ai-provider",
                    request.correlationId(),
                    "Prepared prompt with live tool evidence correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            toolEvidenceListener.onToolEvidenceUpdated(new AnalysisEvidenceSection(
                    "gitlab",
                    "tool-fetched-code",
                    List.of(new AnalysisEvidenceItem(
                            "crm-customer-client-service file src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                            List.of(
                                    new AnalysisEvidenceAttribute(
                                            "filePath",
                                            "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java"
                                    ),
                                    new AnalysisEvidenceAttribute(
                                            "reason",
                                            "Sprawdzam fragment klienta z timeoutem."
                                    ),
                                    new AnalysisEvidenceAttribute("startLine", "5"),
                                    new AnalysisEvidenceAttribute(
                                            "content",
                                            "public class CustomerProfileClient {\n    void configure() {\n        timeout(Duration.ofSeconds(2));\n    }\n}"
                                    )
                            )
                    ))
            ));
            toolEvidencePublished.countDown();
            awaitFinishSignal();
            return new InitialAnalysisResponse(
                    "blocking-tool-ai-provider",
                    "DOWNSTREAM_TIMEOUT",
                    "Catalog profile lookup",
                    "Catalog Context",
                    "Core Integration Team",
                    "Analiza funkcjonalna: timeout dotyka pobrania katalogu w procesie katalogowym.",
                    "Analiza techniczna: plik pobrany przez GitLab potwierdza konfiguracje timeoutu klienta.",
                    "medium",
                    List.of(),
                    prepared.prompt()
            );
        }

        private boolean awaitToolEvidencePublication() throws InterruptedException {
            return toolEvidencePublished.await(2, TimeUnit.SECONDS);
        }

        private void finish() {
            finishSignal.countDown();
        }

        private void awaitFinishSignal() {
            try {
                finishSignal.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to finish AI analysis.", exception);
            }
        }
    }

    private static final class FeedbackAwareInitialAnalysisProvider implements InitialAnalysisProvider {

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "feedback-aware-ai-provider",
                    request.correlationId(),
                    "Prepared prompt with tool feedback correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener,
                AnalysisAiActivityListener activityListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            toolEvidenceListener.onToolEvidenceUpdated(AnalysisAiToolFeedbackEvidenceMapper.toSection(new AnalysisAiToolFeedback(
                    "feedback-1",
                    "gitlab_find_flow_context",
                    "tool-call-1",
                    "feedback-call-1",
                    "partial",
                    "partial",
                    "incomplete",
                    "tool_description",
                    "high",
                    "Wynik toola byl czesciowy i wymaga doprecyzowania opisu.",
                    "Dopisać w opisie toola, jaki zakres flow jest zwracany.",
                    null
            )));
            return new InitialAnalysisResponse(
                    "feedback-aware-ai-provider",
                    "DOWNSTREAM_TIMEOUT",
                    "Catalog profile lookup",
                    "Catalog Context",
                    "Core Integration Team",
                    "Analiza funkcjonalna: timeout dotyka pobrania katalogu w procesie katalogowym.",
                    "Analiza techniczna: feedback toola jest zapisany osobno od deterministic evidence.",
                    "medium",
                    List.of(),
                    prepared.prompt()
            );
        }

        @Override
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            return analyze(
                    preparedAnalysis,
                    toolEvidenceListener,
                    AnalysisAiActivityListener.NO_OP
            );
        }
    }

    private static final class TestAnalysisChatProvider implements AnalysisAiChatProvider {

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            return new AnalysisAiChatResponse(
                    "test-chat-provider",
                    "Follow-up answer for " + request.message(),
                    "Synthetic follow-up prompt for " + request.correlationId()
            );
        }
    }

    private static final class ToolAwareTestAnalysisChatProvider implements AnalysisAiChatProvider {

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            toolEvidenceListener.onToolEvidenceUpdated(new AnalysisEvidenceSection(
                    "gitlab",
                    "tool-fetched-code",
                    List.of(new AnalysisEvidenceItem(
                            "crm-customer-client-service file src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                            List.of(
                                    new AnalysisEvidenceAttribute(
                                            "filePath",
                                            "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java"
                                    ),
                                    new AnalysisEvidenceAttribute(
                                            "reason",
                                            "Potwierdzam miejsce konfiguracji timeoutu."
                                    ),
                                    new AnalysisEvidenceAttribute(
                                            "content",
                                            "timeout(Duration.ofSeconds(2));"
                                    )
                            )
                    ))
            ));

            return new AnalysisAiChatResponse(
                    "test-chat-provider",
                    "Potwierdzilem w repo, ze timeout jest ustawiany w kliencie katalogu.",
                    "Synthetic follow-up prompt for " + request.correlationId()
            );
        }
    }

    private static final class FeedbackAwareTestAnalysisChatProvider implements AnalysisAiChatProvider {

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener,
                AnalysisAiActivityListener activityListener
        ) {
            toolEvidenceListener.onToolEvidenceUpdated(AnalysisAiToolFeedbackEvidenceMapper.toSection(new AnalysisAiToolFeedback(
                    "feedback-chat-1",
                    "db_find_tables",
                    "db-call-1",
                    "feedback-call-chat-1",
                    "not_useful",
                    "no",
                    "wrong_scope",
                    "adapter_result",
                    "medium",
                    "DB tool nie zwrocil tabel pasujacych do pytania operatora.",
                    "Ulepszyć ranking tabel dla aliasow aplikacji.",
                    null
            )));

            return new AnalysisAiChatResponse(
                    "test-chat-provider",
                    "Zapisalem feedback do wyniku toola.",
                    "Synthetic follow-up prompt for " + request.correlationId()
            );
        }

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            return chat(
                    request,
                    toolEvidenceListener,
                    AnalysisAiActivityListener.NO_OP
            );
        }
    }

    private static TestPreparedAnalysis testPreparedAnalysis(InitialAnalysisPreparation preparedAnalysis) {
        if (preparedAnalysis instanceof TestPreparedAnalysis testPreparedAnalysis) {
            return testPreparedAnalysis;
        }

        throw new IllegalArgumentException("Unsupported prepared analysis: " + preparedAnalysis);
    }

    private record TestPreparedAnalysis(
            String providerName,
            String correlationId,
            String prompt,
            InitialAnalysisRequest request
    ) implements InitialAnalysisPreparation {
    }

}
