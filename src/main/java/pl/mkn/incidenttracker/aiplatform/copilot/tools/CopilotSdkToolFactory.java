package pl.mkn.incidenttracker.aiplatform.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CopilotSdkToolFactory {

    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper;
    private final List<CopilotToolDescriptionCustomizer> descriptionCustomizers;
    private final CopilotToolInvocationHandler invocationHandler;

    public List<ToolDefinition> createToolDefinitions(CopilotToolSessionContext sessionContext) {
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
        var customizedDescription = customizeDescription(
                springToolDefinition.name(),
                springToolDefinition.description()
        );

        return ToolDefinition.createSkipPermission(
                springToolDefinition.name(),
                customizedDescription,
                parseInputSchema(springToolDefinition.inputSchema()),
                invocation -> invocationHandler.invokeSpringToolCallback(callback, invocation, sessionContext)
        );
    }

    private String customizeDescription(String toolName, String description) {
        var customizedDescription = description != null ? description : "";
        for (var customizer : descriptionCustomizers) {
            customizedDescription = customizer.customize(toolName, customizedDescription);
            if (customizedDescription == null) {
                customizedDescription = "";
            }
        }
        return customizedDescription;
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
