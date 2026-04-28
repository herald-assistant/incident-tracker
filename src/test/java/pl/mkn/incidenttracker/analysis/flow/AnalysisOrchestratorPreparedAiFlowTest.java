package pl.mkn.incidenttracker.analysis.flow;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextAdapter;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.analysis.TestOperationalContextProjectPathResolver;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiPreparedAnalysis;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalysisOrchestratorPreparedAiFlowTest {

    @Test
    void shouldPreparePromptOnceAndAnalyzeSamePreparedRequest() {
        var provider = new PreparedFlowProvider(false);
        var orchestrator = orchestrator(provider);

        var execution = orchestrator.analyze("timeout-123");

        assertEquals(1, provider.prepareCalls);
        assertEquals(1, provider.analyzePreparedCalls);
        assertEquals(0, provider.analyzeRequestCalls);
        assertSame(provider.prepared, provider.analyzedPrepared);
        assertTrue(provider.prepared.closed);
        assertEquals("Prepared prompt for timeout-123", execution.preparedPrompt());
        assertEquals("Prepared prompt for timeout-123", execution.result().prompt());
    }

    @Test
    void shouldPassAiOptionsToPreparedAnalysisRequest() {
        var provider = new PreparedFlowProvider(false);
        var orchestrator = orchestrator(provider);

        orchestrator.analyze(
                "timeout-123",
                new AnalysisAiOptions("gpt-5.4", "high"),
                AnalysisExecutionListener.NO_OP
        );

        assertEquals("gpt-5.4", provider.preparedRequest.options().model());
        assertEquals("high", provider.preparedRequest.options().reasoningEffort());
    }

    @Test
    void shouldClosePreparedRequestWhenAiExecutionFails() {
        var provider = new PreparedFlowProvider(true);
        var orchestrator = orchestrator(provider);

        var exception = assertThrows(IllegalStateException.class, () -> orchestrator.analyze("timeout-123"));

        assertEquals("AI execution failed", exception.getMessage());
        assertEquals(1, provider.prepareCalls);
        assertEquals(1, provider.analyzePreparedCalls);
        assertTrue(provider.prepared.closed);
    }

    private AnalysisOrchestrator orchestrator(AnalysisAiProvider analysisAiProvider) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("sample/runtime");
        var deploymentContextResolver = new DeploymentContextResolver();

        return new AnalysisOrchestrator(
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
                analysisAiProvider,
                gitLabProperties
        );
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

    private static final class PreparedFlowProvider implements AnalysisAiProvider {

        private final boolean failOnAnalyze;
        private int prepareCalls;
        private int analyzePreparedCalls;
        private int analyzeRequestCalls;
        private TrackingPreparedAnalysis prepared;
        private AnalysisAiPreparedAnalysis analyzedPrepared;
        private AnalysisAiAnalysisRequest preparedRequest;

        private PreparedFlowProvider(boolean failOnAnalyze) {
            this.failOnAnalyze = failOnAnalyze;
        }

        @Override
        public AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) {
            prepareCalls++;
            preparedRequest = request;
            prepared = new TrackingPreparedAnalysis(
                    "prepared-test-provider",
                    request.correlationId(),
                    "Prepared prompt for " + request.correlationId()
            );
            return prepared;
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(
                AnalysisAiPreparedAnalysis preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            analyzePreparedCalls++;
            analyzedPrepared = preparedAnalysis;
            if (failOnAnalyze) {
                throw new IllegalStateException("AI execution failed");
            }
            return response(preparedAnalysis.prompt());
        }

        @Override
        public AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request) {
            analyzeRequestCalls++;
            return response("Legacy response prompt for " + request.correlationId());
        }

        private AnalysisAiAnalysisResponse response(String prompt) {
            return new AnalysisAiAnalysisResponse(
                    "prepared-test-provider",
                    "Prepared flow summary",
                    "PREPARED_FLOW",
                    "Use the prepared request.",
                    "The orchestrator reused one prepared request.",
                    "Prepared request execution path",
                    "Prepared process",
                    "Prepared Context",
                    "Prepared Team",
                    prompt
            );
        }
    }

    private static final class TrackingPreparedAnalysis implements AnalysisAiPreparedAnalysis {

        private final String providerName;
        private final String correlationId;
        private final String prompt;
        private boolean closed;

        private TrackingPreparedAnalysis(String providerName, String correlationId, String prompt) {
            this.providerName = providerName;
            this.correlationId = correlationId;
            this.prompt = prompt;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public String correlationId() {
            return correlationId;
        }

        @Override
        public String prompt() {
            return prompt;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
