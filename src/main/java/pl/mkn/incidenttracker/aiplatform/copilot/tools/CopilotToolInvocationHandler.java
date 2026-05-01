package pl.mkn.incidenttracker.aiplatform.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolInvocation;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolContextFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationEventPublisher;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationStartedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyResult;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class CopilotToolInvocationHandler {

    private final ObjectMapper objectMapper;
    private final CopilotToolContextFactory toolContextFactory;
    private final List<CopilotToolInvocationPolicy> invocationPolicies;
    private final CopilotToolInvocationEventPublisher eventPublisher;

    public CompletableFuture<Object> invokeSpringToolCallback(
            ToolCallback callback,
            ToolInvocation invocation,
            CopilotToolSessionContext sessionContext
    ) {
        var toolStart = System.nanoTime();
        String argumentsJson = null;
        try {
            argumentsJson = objectMapper.writeValueAsString(
                    invocation.getArguments() != null ? invocation.getArguments() : Map.of()
            );
            var policyRequest = new CopilotToolInvocationPolicyRequest(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson
            );
            beforeInvocation(policyRequest);
            eventPublisher.publish(new CopilotToolInvocationStartedEvent(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson
            ));

            var toolContext = toolContextFactory.buildToolContext(sessionContext, invocation);
            var rawResult = callback.call(argumentsJson, toolContext);
            afterInvocation(new CopilotToolInvocationPolicyResult(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson,
                    rawResult
            ));
            eventPublisher.publish(new CopilotToolInvocationFinishedEvent(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson,
                    CopilotToolInvocationOutcome.COMPLETED,
                    rawResult,
                    nanosToMillis(toolStart),
                    null
            ));
            var parsedResult = parseToolResult(rawResult);

            return CompletableFuture.completedFuture(parsedResult);
        }
        catch (CopilotToolInvocationRejectedException exception) {
            var rejectedResult = exception.result();
            eventPublisher.publish(new CopilotToolInvocationFinishedEvent(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson,
                    CopilotToolInvocationOutcome.REJECTED,
                    serializeResult(rejectedResult),
                    nanosToMillis(toolStart),
                    exception
            ));
            return CompletableFuture.completedFuture(rejectedResult);
        }
        catch (Exception exception) {
            eventPublisher.publish(new CopilotToolInvocationFinishedEvent(
                    sessionContext,
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    argumentsJson,
                    CopilotToolInvocationOutcome.FAILED,
                    null,
                    nanosToMillis(toolStart),
                    exception
            ));
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        for (var policy : invocationPolicies) {
            policy.beforeInvocation(request);
        }
    }

    private void afterInvocation(CopilotToolInvocationPolicyResult result) {
        for (var policy : invocationPolicies) {
            policy.afterInvocation(result);
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

    private String serializeResult(Object result) {
        if (result == null) {
            return null;
        }

        if (result instanceof String stringResult) {
            return stringResult;
        }

        try {
            return objectMapper.writeValueAsString(result);
        }
        catch (JsonProcessingException exception) {
            return String.valueOf(result);
        }
    }

    private long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
