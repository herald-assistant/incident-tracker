package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkToolBridge {

    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper;
    private final CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry;

    public List<ToolDefinition> buildToolDefinitions() {
        return toolCallbacksByName().values().stream()
                .sorted(Comparator.comparing(callback -> callback.getToolDefinition().name()))
                .map(this::toCopilotToolDefinition)
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

    private ToolDefinition toCopilotToolDefinition(ToolCallback callback) {
        var springToolDefinition = callback.getToolDefinition();

        return ToolDefinition.createSkipPermission(
                springToolDefinition.name(),
                springToolDefinition.description(),
                parseInputSchema(springToolDefinition.inputSchema()),
                invocation -> invokeSpringToolCallback(callback, invocation)
        );
    }

    private CompletableFuture<Object> invokeSpringToolCallback(ToolCallback callback, ToolInvocation invocation) {
        try {
            var argumentsJson = objectMapper.writeValueAsString(
                    invocation.getArguments() != null ? invocation.getArguments() : Map.of()
            );
            log.info(
                    "Copilot tool invocation request sessionId={} toolCallId={} toolName={} arguments={}",
                    invocation.getSessionId(),
                    invocation.getToolCallId(),
                    invocation.getToolName(),
                    abbreviate(argumentsJson, 500)
            );

            var rawResult = callback.call(argumentsJson);
            toolEvidenceCaptureRegistry.captureToolResult(
                    invocation.getSessionId(),
                    invocation.getToolName(),
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
