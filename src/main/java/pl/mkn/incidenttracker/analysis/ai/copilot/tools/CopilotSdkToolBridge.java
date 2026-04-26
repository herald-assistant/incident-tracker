package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolMetrics;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetExceededResult;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetGuard;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetRegistry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class CopilotSdkToolBridge {

    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper;
    private final CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry;
    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotMetricsLogger metricsLogger;
    private final CopilotToolBudgetGuard budgetGuard;
    private final CopilotToolDescriptionDecorator descriptionDecorator;

    @Autowired
    public CopilotSdkToolBridge(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotMetricsLogger metricsLogger,
            CopilotToolBudgetGuard budgetGuard,
            CopilotToolDescriptionDecorator descriptionDecorator
    ) {
        this.toolCallbackProviders = toolCallbackProviders;
        this.objectMapper = objectMapper;
        this.toolEvidenceCaptureRegistry = toolEvidenceCaptureRegistry;
        this.metricsRegistry = metricsRegistry;
        this.metricsLogger = metricsLogger;
        this.budgetGuard = budgetGuard;
        this.descriptionDecorator = descriptionDecorator;
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
                invocation -> invokeSpringToolCallback(callback, invocation, sessionContext)
        );
    }

    private CompletableFuture<Object> invokeSpringToolCallback(
            ToolCallback callback,
            ToolInvocation invocation,
            CopilotToolSessionContext sessionContext
    ) {
        var toolStart = System.nanoTime();
        var metricsRecorded = false;
        try {
            validateSessionId(sessionContext, invocation);
            var argumentsJson = objectMapper.writeValueAsString(
                    invocation.getArguments() != null ? invocation.getArguments() : Map.of()
            );
            var budgetDecision = budgetGuard.beforeInvocation(
                    invocation.getSessionId(),
                    invocation.getToolName(),
                    argumentsJson
            );
            if (budgetDecision.denied()) {
                return CompletableFuture.completedFuture(CopilotToolBudgetExceededResult.from(budgetDecision));
            }

            var toolContext = buildToolContext(sessionContext, invocation);
            log.info(
                    "Copilot tool invocation request expectedSessionId={} actualSessionId={} toolCallId={} toolName={} arguments={}",
                    sessionContext.copilotSessionId(),
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    abbreviate(argumentsJson, 500)
            );

            var rawResult = callback.call(argumentsJson, toolContext);
            budgetGuard.afterInvocation(invocation.getSessionId(), invocation.getToolName(), rawResult);
            recordToolMetrics(sessionContext, invocation, rawResult, toolStart);
            metricsRecorded = true;
            toolEvidenceCaptureRegistry.captureToolResult(
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson,
                    rawResult
            );
            var parsedResult = parseToolResult(rawResult);

            log.info(
                    "Copilot tool invocation result sessionId={} toolCallId={} toolName={} rawResultLength={} resultPreview={}",
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    rawResult != null ? rawResult.length() : 0,
                    abbreviate(serializeResultPreview(parsedResult), 500)
            );

            return CompletableFuture.completedFuture(parsedResult);
        }
        catch (Exception exception) {
            if (!metricsRecorded) {
                recordToolMetrics(sessionContext, invocation, null, toolStart);
            }
            log.error(
                    "Copilot tool invocation failed sessionId={} toolCallId={} toolName={} error={}",
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    exception.getMessage(),
                    exception
            );
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void recordToolMetrics(
            CopilotToolSessionContext sessionContext,
            ToolInvocation invocation,
            String rawResult,
            long toolStart
    ) {
        if (sessionContext == null || invocation == null) {
            return;
        }

        var toolMetrics = CopilotToolMetrics.from(
                sessionContext.analysisRunId(),
                sessionContext.copilotSessionId(),
                invocation.getToolCallId(),
                invocation.getToolName(),
                (System.nanoTime() - toolStart) / 1_000_000,
                rawResult
        );
        metricsRegistry.recordToolCall(toolMetrics);
        metricsLogger.logToolEvent(toolMetrics);
    }

    private void validateSessionId(CopilotToolSessionContext sessionContext, ToolInvocation invocation) {
        if (sessionContext == null
                || !StringUtils.hasText(sessionContext.copilotSessionId())
                || !StringUtils.hasText(invocation.getSessionId())) {
            return;
        }

        if (!sessionContext.copilotSessionId().equals(invocation.getSessionId())) {
            throw new IllegalStateException(
                    "Copilot tool invocation sessionId mismatch. expected=%s actual=%s tool=%s"
                            .formatted(
                                    sessionContext.copilotSessionId(),
                                    invocation.getSessionId(),
                                    invocation.getToolName()
                            )
            );
        }
    }

    private ToolContext buildToolContext(CopilotToolSessionContext sessionContext, ToolInvocation invocation) {
        var context = new LinkedHashMap<String, Object>();

        if (sessionContext != null) {
            putIfNotBlank(context, CopilotToolContextKeys.ANALYSIS_RUN_ID, sessionContext.analysisRunId());
            putIfNotBlank(context, CopilotToolContextKeys.COPILOT_SESSION_ID, sessionContext.copilotSessionId());
            putIfNotBlank(context, CopilotToolContextKeys.CORRELATION_ID, sessionContext.correlationId());
            putIfNotBlank(context, CopilotToolContextKeys.ENVIRONMENT, sessionContext.environment());
            putIfNotBlank(context, CopilotToolContextKeys.GITLAB_BRANCH, sessionContext.gitLabBranch());
            putIfNotBlank(context, CopilotToolContextKeys.GITLAB_GROUP, sessionContext.gitLabGroup());
        }

        if (invocation != null) {
            putIfNotBlank(context, CopilotToolContextKeys.ACTUAL_COPILOT_SESSION_ID, invocation.getSessionId());
            putIfNotBlank(context, CopilotToolContextKeys.TOOL_CALL_ID, invocation.getToolCallId());
            putIfNotBlank(context, CopilotToolContextKeys.TOOL_NAME, invocation.getToolName());
        }

        return new ToolContext(context);
    }

    private void putIfNotBlank(Map<String, Object> context, String key, String value) {
        if (StringUtils.hasText(value)) {
            context.put(key, value);
        }
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

    private Object parseToolResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return Map.of("status", "empty");
        }

        try {
            return objectMapper.readValue(rawResult, Object.class);
        }
        catch (JsonProcessingException exception) {
            return rawResult;
        }
    }

    private String serializeResultPreview(Object parsedResult) {
        try {
            return objectMapper.writeValueAsString(parsedResult);
        }
        catch (JsonProcessingException exception) {
            return String.valueOf(parsedResult);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }

}
