package pl.mkn.incidenttracker.analysis.sync;

import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.TestAnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogLoader;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.analysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.flow.AnalysisOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AnalysisServiceTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    private final AnalysisService analysisService = new AnalysisService(
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
                    new TestAnalysisAiProvider(),
                    gitLabProperties
            )
        );

    @Test
    void shouldReturnTimeoutAnalysisFromAiForTimeoutCorrelationId() {
        var request = new AnalysisRequest("timeout-123");

        var response = analysisService.analyze(request);

        assertEquals("COMPLETED", response.status());
        assertEquals("timeout-123", response.correlationId());
        assertEquals("dev3", response.environment());
        assertEquals("dev/atlas", response.gitLabBranch());
        assertEquals("DOWNSTREAM_TIMEOUT", response.detectedProblem());
        assertEquals(
                "Structured evidence points to a downstream timeout in the catalog-service call chain.",
                response.summary()
        );
        assertEquals(
                "Inspect recent HTTP client timeout changes, compare downstream latency percentiles, and add focused timeout diagnostics around the slow dependency.",
                response.recommendedAction()
        );
        assertEquals(
                "The test AI provider correlated timeout signals from logs, Dynatrace runtime evidence, and recent GitLab change hints.",
                response.rationale()
        );
        assertEquals(
                "The affected function is the outbound catalog lookup path used while building the billing-side response for the incident flow.",
                response.affectedFunction()
        );
        assertEquals(
                "Synthetic AI prompt for correlationId=timeout-123, environment=dev3, gitLabBranch=dev/atlas",
                response.prompt()
        );
    }

    @Test
    void shouldReturnDatabaseLockAnalysisFromAiForDatabaseLockCorrelationId() {
        var request = new AnalysisRequest("db-lock-123");

        var response = analysisService.analyze(request);

        assertEquals("COMPLETED", response.status());
        assertEquals("db-lock-123", response.correlationId());
        assertEquals("dev1", response.environment());
        assertEquals("dev/zephyr", response.gitLabBranch());
        assertEquals("DATABASE_LOCK", response.detectedProblem());
        assertEquals(
                "Structured evidence points to database lock contention during order write operations.",
                response.summary()
        );
        assertEquals(
                "Review transaction scope changes first, inspect blocked sessions, and narrow long-running write transactions in the affected persistence flow.",
                response.recommendedAction()
        );
        assertEquals(
                "The test AI provider connected lock-related log and Dynatrace runtime signals with the recent GitLab persistence-layer hint.",
                response.rationale()
        );
        assertEquals(
                "The affected function is the order persistence write path that coordinates domain validation and database update steps before the flow can complete.",
                response.affectedFunction()
        );
        assertEquals(
                "Synthetic AI prompt for correlationId=db-lock-123, environment=dev1, gitLabBranch=dev/zephyr",
                response.prompt()
        );
    }

    @Test
    void shouldReturnUnknownAnalysisWhenAiDoesNotFindStrongPattern() {
        var request = new AnalysisRequest("corr-123");

        var response = analysisService.analyze(request);

        assertEquals("COMPLETED", response.status());
        assertEquals("corr-123", response.correlationId());
        assertEquals("dev2", response.environment());
        assertEquals("dev/quartz", response.gitLabBranch());
        assertEquals("UNKNOWN", response.detectedProblem());
        assertEquals("Structured evidence is not yet strong enough for a confident diagnosis.", response.summary());
        assertEquals(
                "Collect more evidence from logs, Dynatrace runtime signals, and recent code changes before proposing a code-level fix.",
                response.recommendedAction()
        );
        assertEquals(
                "The test AI provider could not find a strong pattern in the available evidence.",
                response.rationale()
        );
        assertEquals("", response.affectedFunction());
        assertEquals(
                "Synthetic AI prompt for correlationId=corr-123, environment=dev2, gitLabBranch=dev/quartz",
                response.prompt()
        );
    }

    @Test
    void shouldThrowWhenDiagnosticDataIsMissingForCorrelationId() {
        var request = new AnalysisRequest("not-found");

        var exception = assertThrows(AnalysisDataNotFoundException.class, () -> analysisService.analyze(request));

        assertEquals("No diagnostic data found for correlationId: not-found", exception.getMessage());
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
                new OperationalContextCatalogLoader(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );
    }

    private static TaskExecutor directTaskExecutor() {
        return Runnable::run;
    }

}

