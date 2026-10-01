package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.util.List;

/** Estimates the actual message and session instructions, without double-counting inline artifacts. */
public final class CopilotInitialContextEstimator {

    private CopilotInitialContextEstimator() {
    }

    public static CopilotInitialContextEstimate estimate(
            String prompt,
            SystemMessageConfig systemMessage,
            List<ToolDefinition> tools,
            CopilotSdkProperties.ContextTierPolicy settings
    ) {
        long characters = length(prompt);
        if (tools != null) {
            for (var tool : tools) {
                if (tool != null) {
                    characters += length(tool.name()) + length(tool.description()) + length(tool.parameters());
                }
            }
        }
        if (systemMessage != null) {
            characters += length(systemMessage.getContent());
            if (systemMessage.getSections() != null) {
                for (var entry : systemMessage.getSections().entrySet()) {
                    characters += length(entry.getKey());
                    if (entry.getValue() != null) {
                        characters += length(entry.getValue().getAction()) + length(entry.getValue().getContent());
                    }
                }
            }
        }
        return new CopilotInitialContextEstimate(
                (long) Math.ceil(characters / settings.getEstimatedCharactersPerToken()),
                settings.getReservedTokens()
        );
    }

    private static long length(Object value) {
        return value != null ? String.valueOf(value).length() : 0L;
    }
}
