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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames.PREFIX;

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

            try {
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
            catch (Exception exception) {
                var errorResult = toolErrorResult(invocation, exception);
                eventPublisher.publish(new CopilotToolInvocationFinishedEvent(
                        sessionContext,
                        invocation.getSessionId(),
                        invocation.getToolCallId(),
                        invocation.getToolName(),
                        argumentsJson,
                        CopilotToolInvocationOutcome.FAILED,
                        serializeResult(errorResult),
                        nanosToMillis(toolStart),
                        exception
                ));
                return CompletableFuture.completedFuture(errorResult);
            }
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

    private Map<String, Object> toolErrorResult(ToolInvocation invocation, Exception exception) {
        var rootCause = rootCause(exception);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "tool_error");
        result.put("toolName", invocation.getToolName());
        result.put("toolCallId", invocation.getToolCallId());
        result.put("errorType", exception.getClass().getSimpleName());
        result.put("rootCauseType", rootCause.getClass().getSimpleName());
        result.put("message", safeMessage(exception));
        result.put("rootCauseMessage", safeMessage(rootCause));
        result.put("retryableWithChangedArguments", true);
        result.put("recommendation", retryRecommendation(invocation.getToolName(), exception));
        result.put(
                "instruction",
                "Nie powtarzaj identycznego wywolania. Zmien argumenty toola albo uzyj discovery/describe toola, a jesli nie da sie sprawdzic hipotezy, opisz limit widocznosci."
        );
        return result;
    }

    private Throwable rootCause(Throwable exception) {
        var current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable exception) {
        var message = exception != null ? exception.getMessage() : null;
        if (message == null || message.isBlank()) {
            return "Tool invocation failed without an error message.";
        }
        return abbreviate(message.replaceAll("\\s+", " ").trim(), 1_000);
    }

    private String retryRecommendation(String toolName, Throwable exception) {
        var message = (safeMessage(exception) + " " + safeMessage(rootCause(exception))).toUpperCase(java.util.Locale.ROOT);
        if (toolName != null && toolName.startsWith(PREFIX)) {
            if (message.contains("ORA-00904")) {
                return "Kolumna lub alias nie istnieje w widocznym scope. Uzyj db_describe_table albo db_find_columns i ponow zapytanie z poprawna nazwa kolumny.";
            }
            if (message.contains("ORA-00942")) {
                return "Tabela lub widok nie jest widoczny w scope. Uzyj db_find_tables i db_describe_table dla dokladnego schema.table przed ponownym zapytaniem.";
            }
            if (message.contains("ORA-00932") || message.contains("CLOB") || message.contains("LOB")) {
                return "Operator lub wartosc nie pasuje do typu kolumny. Sprawdz typy przez db_describe_table; dla LOB preferuj IS_NULL/IS_NOT_NULL, dokladny krotki tekst albo inna techniczna kolumne filtra.";
            }
            if (message.contains("ORA-01722")) {
                return "Wartosc filtra nie pasuje do typu liczbowego. Sprawdz typ kolumny przez db_describe_table i ponow z wartoscia w odpowiednim formacie albo innym operatorem.";
            }
            if (message.contains("REQUIRES EXACTLY ONE VALUE") || message.contains("REQUIRES AT LEAST ONE VALUE")) {
                return "Popraw ksztalt filtra: operator jednoargumentowy wymaga jednej wartosci, a IN/NOT_IN listy wartosci. Ponow wywolanie ze zmienionym values.";
            }
            return "Zmien typed DB query: sprawdz dokladne schema.table, kolumny i typy przez db_find_tables/db_find_columns/db_describe_table, potem ponow z poprawionymi filtrami lub operatorem.";
        }
        return "Zmien argumenty toola zgodnie z komunikatem bledu; jesli scope albo nazwa zasobu jest niepewna, uzyj najpierw discovery/list/search toola.";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(" + value.length() + " chars)";
    }

    private long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
