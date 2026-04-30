package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

import java.util.List;

public record CopilotSessionConfigRequest(
        CopilotToolSessionContext context,
        List<ToolDefinition> tools,
        List<String> availableToolNames,
        List<String> skillDirectories,
        AnalysisAiOptions options,
        String deniedToolUseMessage
) {

    public static final String DEFAULT_DENIED_TOOL_USE_MESSAGE =
            "Use only the explicitly enabled tools for this session.";

    public CopilotSessionConfigRequest {
        tools = tools != null ? List.copyOf(tools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        skillDirectories = skillDirectories != null ? List.copyOf(skillDirectories) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
        deniedToolUseMessage = hasText(deniedToolUseMessage)
                ? deniedToolUseMessage
                : DEFAULT_DENIED_TOOL_USE_MESSAGE;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
