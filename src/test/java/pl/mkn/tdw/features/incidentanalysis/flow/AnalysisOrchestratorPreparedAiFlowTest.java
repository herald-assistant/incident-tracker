package pl.mkn.tdw.features.incidentanalysis.flow;

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
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisProvider;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisEvidenceCollector;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;

import java.util.List;

import pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace.DynatraceEvidenceProviderTestCreator;
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

    private AnalysisOrchestrator orchestrator(InitialAnalysisProvider initialAnalysisProvider) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        var deploymentContextResolver = new DeploymentContextResolver();

        return new AnalysisOrchestrator(
                new AnalysisEvidenceCollector(
                        new ElasticLogEvidenceProvider(new TestElasticLogPort()),
                        new DeploymentContextEvidenceProvider(deploymentContextResolver),
                        DynatraceEvidenceProviderTestCreator.create(new TestDynatraceIncidentPort(), deploymentContextResolver),
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

    private static final class PreparedFlowProvider implements InitialAnalysisProvider {

        private final boolean failOnAnalyze;
        private int prepareCalls;
        private int analyzePreparedCalls;
        private TrackingPreparedAnalysis prepared;
        private InitialAnalysisPreparation analyzedPrepared;
        private InitialAnalysisRequest preparedRequest;

        private PreparedFlowProvider(boolean failOnAnalyze) {
            this.failOnAnalyze = failOnAnalyze;
        }

        @Override
        public InitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
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
        public InitialAnalysisResponse analyze(
                InitialAnalysisPreparation preparedAnalysis,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            analyzePreparedCalls++;
            analyzedPrepared = preparedAnalysis;
            if (failOnAnalyze) {
                throw new IllegalStateException("AI execution failed");
            }
            return response(preparedAnalysis.prompt());
        }

        private InitialAnalysisResponse response(String prompt) {
            return new InitialAnalysisResponse(
                    "prepared-test-provider",
                    "PREPARED_FLOW",
                    "Prepared process",
                    "Prepared Context",
                    "Prepared Team",
                    "Prepared functional analysis",
                    "Prepared technical analysis",
                    "high",
                    List.of(),
                    prompt
            );
        }
    }

    private static final class TrackingPreparedAnalysis implements InitialAnalysisPreparation {

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
