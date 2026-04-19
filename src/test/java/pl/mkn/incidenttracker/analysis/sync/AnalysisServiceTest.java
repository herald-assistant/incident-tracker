package pl.mkn.incidenttracker.analysis.sync;

import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.AnalysisMode;
import pl.mkn.incidenttracker.analysis.AnalysisVariantStatus;
import pl.mkn.incidenttracker.analysis.TestAnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.exploratory.ExploratoryAnalysisProperties;
import pl.mkn.incidenttracker.analysis.evidence.provider.exploratory.ExploratoryFlowEvidenceProvider;
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
                            disabledExploratoryFlowEvidenceProvider()
                    ),
                    new TestAnalysisAiProvider(),
                    gitLabProperties,
                    disabledExploratoryProperties()
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
        assertEquals("DOWNSTREAM_TIMEOUT", response.variants().conservative().detectedProblem());
        assertEquals(
                "Structured evidence points to a downstream timeout in the catalog-service call chain.",
                response.variants().conservative().summary()
        );
        assertEquals(
                "Inspect recent HTTP client timeout changes, compare downstream latency percentiles, and add focused timeout diagnostics around the slow dependency.",
                response.variants().conservative().recommendedAction()
        );
        assertEquals(
                "The test AI provider correlated timeout signals from logs, Dynatrace runtime evidence, and recent GitLab change hints.",
                response.variants().conservative().rationale()
        );
        assertEquals(
                "Synthetic AI prompt for mode=CONSERVATIVE, correlationId=timeout-123, environment=dev3, gitLabBranch=dev/atlas",
                response.variants().conservative().prompt()
        );
        assertEquals(AnalysisMode.CONSERVATIVE, response.variants().conservative().mode());
        assertEquals(AnalysisVariantStatus.DISABLED, response.variants().exploratory().status());
    }

    @Test
    void shouldReturnDatabaseLockAnalysisFromAiForDatabaseLockCorrelationId() {
        var request = new AnalysisRequest("db-lock-123");

        var response = analysisService.analyze(request);

        assertEquals("COMPLETED", response.status());
        assertEquals("db-lock-123", response.correlationId());
        assertEquals("dev1", response.environment());
        assertEquals("dev/zephyr", response.gitLabBranch());
        assertEquals("DATABASE_LOCK", response.variants().conservative().detectedProblem());
        assertEquals(
                "Structured evidence points to database lock contention during order write operations.",
                response.variants().conservative().summary()
        );
        assertEquals(
                "Review transaction scope changes first, inspect blocked sessions, and narrow long-running write transactions in the affected persistence flow.",
                response.variants().conservative().recommendedAction()
        );
        assertEquals(
                "The test AI provider connected lock-related log and Dynatrace runtime signals with the recent GitLab persistence-layer hint.",
                response.variants().conservative().rationale()
        );
        assertEquals(
                "Synthetic AI prompt for mode=CONSERVATIVE, correlationId=db-lock-123, environment=dev1, gitLabBranch=dev/zephyr",
                response.variants().conservative().prompt()
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
        assertEquals("UNKNOWN", response.variants().conservative().detectedProblem());
        assertEquals("Structured evidence is not yet strong enough for a confident diagnosis.", response.variants().conservative().summary());
        assertEquals(
                "Collect more evidence from logs, Dynatrace runtime signals, and recent code changes before proposing a code-level fix.",
                response.variants().conservative().recommendedAction()
        );
        assertEquals(
                "The test AI provider could not find a strong pattern in the available evidence.",
                response.variants().conservative().rationale()
        );
        assertEquals(
                "Synthetic AI prompt for mode=CONSERVATIVE, correlationId=corr-123, environment=dev2, gitLabBranch=dev/quartz",
                response.variants().conservative().prompt()
        );
    }

    @Test
    void shouldBuildExploratoryVariantWhenExploratoryModeIsEnabled() {
        var gitLabProperties = gitLabProperties();
        gitLabProperties.setBaseUrl("https://gitlab.example.com");
        var exploratoryProperties = new ExploratoryAnalysisProperties();
        exploratoryProperties.setEnabled(true);

        var service = new AnalysisService(
                new AnalysisOrchestrator(
                        new AnalysisEvidenceCollector(
                                new ElasticLogEvidenceProvider(new TestElasticLogPort()),
                                new DeploymentContextEvidenceProvider(deploymentContextResolver),
                                new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
                                new GitLabDeterministicEvidenceProvider(
                                        new TestGitLabRepositoryPort(),
                                        gitLabProperties,
                                        mock(GitLabSourceResolveService.class),
                                        deploymentContextResolver
                                ),
                                disabledOperationalContextEvidenceProvider(),
                                new ExploratoryFlowEvidenceProvider(
                                        new TestGitLabRepositoryPort(),
                                        gitLabProperties,
                                        exploratoryProperties
                                )
                        ),
                        new TestAnalysisAiProvider(),
                        gitLabProperties,
                        exploratoryProperties
                )
        );

        var response = service.analyze(new AnalysisRequest("timeout-123"));

        assertEquals(AnalysisVariantStatus.COMPLETED, response.variants().exploratory().status());
        assertEquals(AnalysisMode.EXPLORATORY, response.variants().exploratory().mode());
        assertEquals("RECONSTRUCTED_FLOW_HYPOTHESIS", response.variants().exploratory().detectedProblem());
        assertEquals(AnalysisMode.CONSERVATIVE, response.variants().conservative().mode());
        assertEquals(AnalysisMode.EXPLORATORY, response.variants().exploratory().mode());
        assertEquals(AnalysisVariantStatus.COMPLETED, response.variants().conservative().status());
        assertEquals(AnalysisVariantStatus.COMPLETED, response.variants().exploratory().status());
        assertEquals("HYPOTHESIS", response.variants().exploratory().problemNature().name());
        assertEquals(2, response.variants().exploratory().diagram().nodes().size());
        assertEquals(1, response.variants().exploratory().diagram().edges().size());
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

    private static ExploratoryFlowEvidenceProvider disabledExploratoryFlowEvidenceProvider() {
        var properties = disabledExploratoryProperties();
        return new ExploratoryFlowEvidenceProvider(mock(GitLabRepositoryPort.class), gitLabProperties(), properties);
    }

    private static ExploratoryAnalysisProperties disabledExploratoryProperties() {
        var properties = new ExploratoryAnalysisProperties();
        properties.setEnabled(false);
        return properties;
    }

}

