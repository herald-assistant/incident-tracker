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

    public static final String SKILL_TOOL_NAME = "skill";
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

    public List<String> effectiveAvailableToolNames() {
        var effectiveToolNames = new java.util.ArrayList<>(availableToolNames);
        if (skillDirectoriesConfigured() && !effectiveToolNames.contains(SKILL_TOOL_NAME)) {
            effectiveToolNames.add(SKILL_TOOL_NAME);
        }
        return List.copyOf(effectiveToolNames);
    }

    public boolean skillToolAvailable() {
        return effectiveAvailableToolNames().contains(SKILL_TOOL_NAME);
    }

    public boolean skillDirectoriesConfigured() {
        return !skillDirectories.isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
