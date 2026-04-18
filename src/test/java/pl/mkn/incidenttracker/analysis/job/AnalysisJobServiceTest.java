package pl.mkn.incidenttracker.analysis.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.flow.AnalysisOrchestrator;
import pl.mkn.incidenttracker.analysis.TestAnalysisAiProvider;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AnalysisJobServiceTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();
    private final CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
    private final AnalysisJobService analysisJobService = analysisJobService(new TestAnalysisAiProvider(), taskExecutor);

    @Test
    void shouldReturnQueuedJobThenCompleteItAfterWorkerRuns() {
        var started = analysisJobService.startAnalysis(new AnalysisRequest("timeout-123"));

        assertNotNull(started.analysisId());
        assertEquals("timeout-123", started.correlationId());
        assertEquals("QUEUED", started.status());
        assertEquals(5, started.steps().size());
        assertEquals("PENDING", started.steps().get(0).status());
        assertFalse(taskExecutor.isEmpty());

        taskExecutor.runNext();

        var completed = analysisJobService.getAnalysis(started.analysisId());

        assertEquals("COMPLETED", completed.status());
        assertEquals("dev3", completed.environment());
        assertEquals("dev/atlas", completed.gitLabBranch());
        assertEquals(2, completed.evidenceSections().size());
        assertEquals(
                "Synthetic AI prompt for correlationId=timeout-123, environment=dev3, gitLabBranch=dev/atlas",
                completed.preparedPrompt()
        );
        assertNotNull(completed.result());
        assertEquals("DOWNSTREAM_TIMEOUT", completed.result().detectedProblem());
        assertEquals("COMPLETED", completed.steps().get(4).status());
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
        assertEquals("SKIPPED", finished.steps().get(4).status());
    }

    @Test
    void shouldKeepPreparedPromptWhenAiAnalysisFails() {
        var failingTaskExecutor = new CapturingTaskExecutor();
        var service = analysisJobService(new FailingAnalysisAiProvider(), failingTaskExecutor);

        var started = service.startAnalysis(new AnalysisRequest("timeout-123"));

        assertEquals("QUEUED", started.status());
        failingTaskExecutor.runNext();

        var finished = service.getAnalysis(started.analysisId());

        assertEquals("FAILED", finished.status());
        assertEquals("ANALYSIS_FAILED", finished.errorCode());
        assertEquals("AI gateway timeout", finished.errorMessage());
        assertEquals(
                "Prepared prompt for external fallback correlationId=timeout-123",
                finished.preparedPrompt()
        );
        assertNull(finished.result());
        assertEquals("FAILED", finished.steps().get(4).status());
    }

    private AnalysisJobService analysisJobService(AnalysisAiProvider analysisAiProvider, TaskExecutor taskExecutor) {
        return new AnalysisJobService(
                new AnalysisOrchestrator(
                        new AnalysisEvidenceCollector(List.of(
                                new ElasticLogEvidenceProvider(new TestElasticLogPort()),
                                new DeploymentContextEvidenceProvider(deploymentContextResolver),
                                new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
                                new GitLabDeterministicEvidenceProvider(
                                        mock(GitLabRepositoryPort.class),
                                        gitLabProperties,
                                        mock(GitLabSourceResolveService.class),
                                        deploymentContextResolver
                                )
                        )),
                        analysisAiProvider,
                        gitLabProperties
                ),
                taskExecutor
        );
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("sample/runtime");
        return properties;
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
        public String preparePrompt(AnalysisAiAnalysisRequest request) {
            return "Prepared prompt for external fallback correlationId=%s"
                    .formatted(request.correlationId());
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
            throw new IllegalStateException("AI gateway timeout");
        }
    }

}

