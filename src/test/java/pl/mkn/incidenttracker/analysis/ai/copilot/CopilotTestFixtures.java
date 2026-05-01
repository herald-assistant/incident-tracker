package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentArtifactService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentArtifactItemIdGenerator;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentDigestService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolBudgetMetricsListener;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolInvocationTelemetryListener;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolContextFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceMapper;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.description.CopilotIncidentToolDescriptionCustomizer;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.description.CopilotIncidentToolGuidanceCatalog;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationEventPublisher;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetPolicy;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget.CopilotToolBudgetRegistry;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.session.CopilotToolSessionValidationPolicy;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.List;

public final class CopilotTestFixtures {

    private CopilotTestFixtures() {
    }

    public static CopilotIncidentArtifactService artifactService(ObjectMapper objectMapper) {
        return new CopilotIncidentArtifactService(
                objectMapper,
                new CopilotIncidentDigestService(),
                new CopilotIncidentArtifactItemIdGenerator()
        );
    }

    public static CopilotToolEvidenceSessionStore toolEvidenceSessionStore(ObjectMapper objectMapper) {
        return new CopilotToolEvidenceSessionStore();
    }

    public static List<CopilotToolDescriptionCustomizer> toolDescriptionCustomizers() {
        return List.of(new CopilotIncidentToolDescriptionCustomizer(new CopilotIncidentToolGuidanceCatalog()));
    }

    public static CopilotSdkToolFactory toolFactory(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger
    ) {
        return toolFactory(
                toolCallbackProviders,
                objectMapper,
                toolEvidenceSessionStore,
                metricsRegistry,
                metricsLogger,
                new CopilotToolBudgetPolicy(
                        new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties()),
                        List.of(new CopilotToolBudgetMetricsListener(metricsRegistry))
                )
        );
    }

    public static CopilotSdkToolFactory toolFactory(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger,
            CopilotToolBudgetPolicy budgetPolicy
    ) {
        return new CopilotSdkToolFactory(
                toolCallbackProviders,
                objectMapper,
                toolDescriptionCustomizers(),
                new CopilotToolInvocationHandler(
                        objectMapper,
                        new CopilotToolContextFactory(),
                        List.of(new CopilotToolSessionValidationPolicy(), budgetPolicy),
                        toolInvocationEventPublisher(
                                objectMapper,
                                toolEvidenceSessionStore,
                                metricsRegistry,
                                metricsLogger
                        )
                )
        );
    }

    public static CopilotToolInvocationEventPublisher toolInvocationEventPublisher(
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger
    ) {
        var payloadReader = new JsonPayloadReader(objectMapper);
        var gitLabListener = new GitLabToolEvidenceCaptureListener(
                toolEvidenceSessionStore,
                new GitLabToolEvidenceMapper(objectMapper, payloadReader)
        );
        var databaseListener = new DatabaseToolEvidenceCaptureListener(
                toolEvidenceSessionStore,
                new DatabaseToolEvidenceMapper(payloadReader)
        );
        var telemetryListener = new CopilotToolInvocationTelemetryListener(metricsRegistry, metricsLogger);

        return new CopilotToolInvocationEventPublisher(event -> {
            if (event instanceof CopilotToolInvocationFinishedEvent finishedEvent) {
                telemetryListener.onToolInvocationFinished(finishedEvent);
                if (finishedEvent.outcome() == CopilotToolInvocationOutcome.COMPLETED) {
                    gitLabListener.onToolInvocationFinished(finishedEvent);
                    databaseListener.onToolInvocationFinished(finishedEvent);
                }
            }
        });
    }

    public static CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotSessionMetricsRegistry metricsRegistry
    ) {
        return new CopilotSdkExecutionGateway(
                properties,
                toolEvidenceSessionStore,
                metricsRegistry,
                new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties())
        );
    }

    public static CopilotMetricsLogger metricsLogger(ObjectMapper objectMapper) {
        return new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);
    }

    public static CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }
}
