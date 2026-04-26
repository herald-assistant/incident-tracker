package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSdkExecutionGateway;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactItemIdGenerator;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotIncidentDigestService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolDescriptionDecorator;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceCaptureRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolGuidanceCatalog;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.DatabaseToolEvidenceMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.GitLabToolEvidenceMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.ToolJsonPayloadReader;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetGuard;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetRegistry;

import java.util.List;

public final class CopilotTestFixtures {

    private CopilotTestFixtures() {
    }

    public static CopilotArtifactService artifactService(ObjectMapper objectMapper) {
        return new CopilotArtifactService(
                objectMapper,
                new CopilotIncidentDigestService(),
                new CopilotArtifactItemIdGenerator()
        );
    }

    public static CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry(ObjectMapper objectMapper) {
        var payloadReader = new ToolJsonPayloadReader(objectMapper);
        return new CopilotToolEvidenceCaptureRegistry(
                new GitLabToolEvidenceMapper(objectMapper, payloadReader),
                new DatabaseToolEvidenceMapper(payloadReader)
        );
    }

    public static CopilotToolDescriptionDecorator toolDescriptionDecorator() {
        return new CopilotToolDescriptionDecorator(new CopilotToolGuidanceCatalog());
    }

    public static CopilotSdkToolBridge toolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger
    ) {
        return toolBridge(
                toolCallbackProviders,
                objectMapper,
                toolEvidenceCaptureRegistry,
                metricsRegistry,
                metricsLogger,
                new CopilotToolBudgetGuard(
                        new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties()),
                        metricsRegistry
                )
        );
    }

    public static CopilotSdkToolBridge toolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger,
            CopilotToolBudgetGuard budgetGuard
    ) {
        return new CopilotSdkToolBridge(
                toolCallbackProviders,
                objectMapper,
                toolDescriptionDecorator(),
                new CopilotToolInvocationHandler(
                        objectMapper,
                        new CopilotToolContextFactory(),
                        toolEvidenceCaptureRegistry,
                        metricsRegistry,
                        metricsLogger,
                        budgetGuard
                )
        );
    }

    public static CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry
    ) {
        return new CopilotSdkExecutionGateway(
                properties,
                toolEvidenceCaptureRegistry,
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
