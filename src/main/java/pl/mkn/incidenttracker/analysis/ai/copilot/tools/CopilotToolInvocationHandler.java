package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolInvocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolMetrics;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetDtos;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetGuard;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotToolInvocationHandler {

    private final ObjectMapper objectMapper;
    private final CopilotToolContextFactory toolContextFactory;
    private final CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry;
    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotMetricsLogger metricsLogger;
    private final CopilotToolBudgetGuard budgetGuard;

    CompletableFuture<Object> invokeSpringToolCallback(
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
                return CompletableFuture.completedFuture(CopilotToolBudgetDtos.deniedResult(budgetDecision));
            }

            var toolContext = toolContextFactory.buildToolContext(sessionContext, invocation);
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
