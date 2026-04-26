package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetGuard;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetRegistry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CopilotSdkToolBridge {

    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper;
    private final CopilotToolDescriptionDecorator descriptionDecorator;
    private final CopilotToolInvocationHandler invocationHandler;

    @Autowired
    public CopilotSdkToolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolDescriptionDecorator descriptionDecorator,
            CopilotToolInvocationHandler invocationHandler
    ) {
        this.toolCallbackProviders = toolCallbackProviders;
        this.objectMapper = objectMapper;
        this.descriptionDecorator = descriptionDecorator;
        this.invocationHandler = invocationHandler;
    }

    public CopilotSdkToolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger,
            CopilotToolBudgetGuard budgetGuard
    ) {
        this(
                toolCallbackProviders,
                objectMapper,
                toolEvidenceCaptureRegistry,
                metricsRegistry,
                metricsLogger,
                budgetGuard,
                new CopilotToolDescriptionDecorator()
        );
    }

    public CopilotSdkToolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger,
            CopilotToolBudgetGuard budgetGuard,
            CopilotToolDescriptionDecorator descriptionDecorator
    ) {
        this(
                toolCallbackProviders,
                objectMapper,
                descriptionDecorator,
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

    public CopilotSdkToolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger
    ) {
        this(
                toolCallbackProviders,
                objectMapper,
                toolEvidenceCaptureRegistry,
                metricsRegistry,
                metricsLogger,
                new CopilotToolBudgetGuard(
                        new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties()),
                        metricsRegistry
                ),
                new CopilotToolDescriptionDecorator()
        );
    }

    public List<ToolDefinition> buildToolDefinitions() {
        var analysisRunId = UUID.randomUUID().toString();
        return buildToolDefinitions(new CopilotToolSessionContext(
                analysisRunId,
                "analysis-" + analysisRunId,
                null,
                null,
                null,
                null
        ));
    }

    public List<ToolDefinition> buildToolDefinitions(CopilotToolSessionContext sessionContext) {
        return toolCallbacksByName().values().stream()
                .sorted(Comparator.comparing(callback -> callback.getToolDefinition().name()))
                .map(callback -> toCopilotToolDefinition(callback, sessionContext))
                .toList();
    }

    private Map<String, ToolCallback> toolCallbacksByName() {
        var callbacksByName = new LinkedHashMap<String, ToolCallback>();

        for (var provider : toolCallbackProviders) {
            for (var callback : provider.getToolCallbacks()) {
                callbacksByName.putIfAbsent(callback.getToolDefinition().name(), callback);
            }
        }

        return callbacksByName;
    }

    private ToolDefinition toCopilotToolDefinition(ToolCallback callback, CopilotToolSessionContext sessionContext) {
        var springToolDefinition = callback.getToolDefinition();
        var decoratedDescription = descriptionDecorator.decorate(
                springToolDefinition.name(),
                springToolDefinition.description()
        );

        return ToolDefinition.createSkipPermission(
                springToolDefinition.name(),
                decoratedDescription,
                parseInputSchema(springToolDefinition.inputSchema()),
                invocation -> invocationHandler.invokeSpringToolCallback(callback, invocation, sessionContext)
        );
    }

    private Map<String, Object> parseInputSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return Map.of(
                    "type", "object",
                    "properties", Map.of()
            );
        }

        try {
            return objectMapper.readValue(inputSchema, Map.class);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse Spring tool input schema.", exception);
        }
    }

}
