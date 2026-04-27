package pl.mkn.incidenttracker.analysis.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextAdapter;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiPreparedAnalysis;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.flow.AnalysisOrchestrator;
import pl.mkn.incidenttracker.analysis.TestAnalysisAiProvider;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalysisJobServiceTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();
    private final CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
    private final AnalysisJobService analysisJobService = analysisJobService(
            new TestAnalysisAiProvider(),
            new TestAnalysisChatProvider(),
            taskExecutor
    );

    @Test
    void shouldReturnQueuedJobThenCompleteItAfterWorkerRuns() {
        var started = analysisJobService.startAnalysis(new AnalysisRequest("timeout-123"));

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
        assertEquals("DOWNSTREAM_TIMEOUT", completed.result().detectedProblem());
        assertEquals(
                "The affected function is the outbound catalog lookup path used while building the billing-side response for the incident flow.",
                completed.result().affectedFunction()
        );
        assertEquals("Billing catalog lookup", completed.result().affectedProcess());
        assertEquals("Billing Context", completed.result().affectedBoundedContext());
        assertEquals("Core Integration Team", completed.result().affectedTeam());
        assertEquals("COMPLETED", completed.steps().get(5).status());
    }

    @Test
    void shouldPassSelectedAiOptionsToAnalysisFlow() {
        var provider = new CapturingOptionsAnalysisAiProvider();
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
    void shouldMarkJobAsNotFoundWhenEvidenceIsMissing() {
        var started = analysisJobService.startAnalysis(new AnalysisRequest("not-found"));

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
                new FailingAnalysisAiProvider(),
                new TestAnalysisChatProvider(),
                failingTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisRequest("timeout-123"));

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
        var toolAwareProvider = new BlockingToolAwareAnalysisAiProvider();
        var blockingTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(
                toolAwareProvider,
                new TestAnalysisChatProvider(),
                blockingTaskExecutor
        );

        var started = service.startAnalysis(new AnalysisRequest("timeout-123"));
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
    void shouldRunFollowUpChatAfterCompletedAnalysis() {
        var chatProvider = new ToolAwareTestAnalysisChatProvider();
        var chatTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(new TestAnalysisAiProvider(), chatProvider, chatTaskExecutor);

        var started = service.startAnalysis(new AnalysisRequest("timeout-123"));
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

    private AnalysisJobService analysisJobService(
            AnalysisAiProvider analysisAiProvider,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor taskExecutor
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
                                        deploymentContextResolver
                                ),
                                disabledOperationalContextEvidenceProvider(),
                                directTaskExecutor()
                        ),
                        analysisAiProvider,
                        gitLabProperties
                ),
                analysisAiChatProvider,
                taskExecutor
        );
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("sample/runtime");
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

    private static final class FailingAnalysisAiProvider implements AnalysisAiProvider {

        @Override
        public AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "failing-ai-provider",
                    request.correlationId(),
                    "Prepared prompt for external fallback correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
            throw new IllegalStateException("AI gateway timeout");
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(
                AnalysisAiPreparedAnalysis preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            throw new IllegalStateException("AI gateway timeout");
        }
    }

    private static final class CapturingOptionsAnalysisAiProvider implements AnalysisAiProvider {

        private AnalysisAiAnalysisRequest lastPreparedRequest;

        @Override
        public AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) {
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
        public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
            try (var prepared = prepare(request)) {
                return analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
            }
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(
                AnalysisAiPreparedAnalysis preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            var options = prepared.request().options() != null
                    ? prepared.request().options()
                    : AnalysisAiOptions.DEFAULT;
            return new AnalysisAiAnalysisResponse(
                    "capturing-options-ai-provider",
                    "Selected model " + options.model(),
                    "OPTIONS_CAPTURED",
                    "Use selected AI runtime options.",
                    "The request carried AI runtime options.",
                    "Selected model analysis path",
                    "Options process",
                    "Options Context",
                    "Options Team",
                    prepared.prompt()
            );
        }
    }

    private static final class BlockingToolAwareAnalysisAiProvider implements AnalysisAiProvider {

        private final CountDownLatch toolEvidencePublished = new CountDownLatch(1);
        private final CountDownLatch finishSignal = new CountDownLatch(1);

        @Override
        public AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) {
            return new TestPreparedAnalysis(
                    "blocking-tool-ai-provider",
                    request.correlationId(),
                    "Prepared prompt with live tool evidence correlationId=%s".formatted(request.correlationId()),
                    request
            );
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
            try (var prepared = prepare(request)) {
                return analyze(prepared, AnalysisAiToolEvidenceListener.NO_OP);
            }
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(
                AnalysisAiPreparedAnalysis preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            var prepared = testPreparedAnalysis(preparedAnalysis);
            toolEvidenceListener.onToolEvidenceUpdated(new AnalysisEvidenceSection(
                    "gitlab",
                    "tool-fetched-code",
                    List.of(new AnalysisEvidenceItem(
                            "edge-client-service file src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                            List.of(
                                    new AnalysisEvidenceAttribute(
                                            "filePath",
                                            "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java"
                                    ),
                                    new AnalysisEvidenceAttribute(
                                            "reason",
                                            "Sprawdzam fragment klienta z timeoutem."
                                    ),
                                    new AnalysisEvidenceAttribute("startLine", "5"),
                                    new AnalysisEvidenceAttribute(
                                            "content",
                                            "public class CatalogGatewayClient {\n    void configure() {\n        timeout(Duration.ofSeconds(2));\n    }\n}"
                                    )
                            )
                    ))
            ));
            toolEvidencePublished.countDown();
            awaitFinishSignal();
            return new AnalysisAiAnalysisResponse(
                    "blocking-tool-ai-provider",
                    "Structured evidence points to a downstream timeout in the catalog-service call chain.",
                    "DOWNSTREAM_TIMEOUT",
                    "Inspect recent HTTP client timeout changes first.",
                    "The tool-fetched GitLab file confirms the timeout configuration.",
                    "The affected function is the outbound catalog lookup path used while building the billing-side response for the incident flow.",
                    "Billing catalog lookup",
                    "Billing Context",
                    "Core Integration Team",
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
                            "edge-client-service file src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                            List.of(
                                    new AnalysisEvidenceAttribute(
                                            "filePath",
                                            "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java"
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

    private static TestPreparedAnalysis testPreparedAnalysis(AnalysisAiPreparedAnalysis preparedAnalysis) {
        if (preparedAnalysis instanceof TestPreparedAnalysis testPreparedAnalysis) {
            return testPreparedAnalysis;
        }

        throw new IllegalArgumentException("Unsupported prepared analysis: " + preparedAnalysis);
    }

    private record TestPreparedAnalysis(
            String providerName,
            String correlationId,
            String prompt,
            AnalysisAiAnalysisRequest request
    ) implements AnalysisAiPreparedAnalysis {
    }

}
