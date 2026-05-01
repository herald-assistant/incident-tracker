package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;

public record CopilotSessionConfigRequest(
        String sessionId,
        List<ToolDefinition> tools,
        List<String> availableToolNames,
        List<String> skillDirectories,
        CopilotModelSelection modelSelection,
        String deniedToolUseMessage
) {

    public static final String DEFAULT_DENIED_TOOL_USE_MESSAGE =
            "Use only the explicitly enabled tools for this session.";

    public CopilotSessionConfigRequest {
        if (!hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        tools = tools != null ? List.copyOf(tools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        skillDirectories = skillDirectories != null ? List.copyOf(skillDirectories) : List.of();
        modelSelection = modelSelection != null ? modelSelection : CopilotModelSelection.DEFAULT;
        deniedToolUseMessage = hasText(deniedToolUseMessage)
                ? deniedToolUseMessage
                : DEFAULT_DENIED_TOOL_USE_MESSAGE;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
