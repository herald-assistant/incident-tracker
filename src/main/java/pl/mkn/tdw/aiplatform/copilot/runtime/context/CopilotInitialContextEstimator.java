package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

/** Conservative estimate; it is not the provider's tokenizer. */
public final class CopilotInitialContextEstimator {

    private CopilotInitialContextEstimator() {}

    public static long estimate(CopilotPreparedSession session, CopilotSdkProperties.ContextTierPolicy settings) {
        long characters = length(session.prompt());
        var tools = session.sessionConfig() != null ? session.sessionConfig().getTools() : null;
        if (tools != null) {
            for (var tool : tools) {
                if (tool != null) characters += length(tool.name()) + length(tool.description()) + length(tool.parameters());
            }
        }
        var message = session.sessionTarget() != null && session.sessionTarget().existing()
                ? session.resumeSessionConfig() != null ? session.resumeSessionConfig().getSystemMessage() : null
                : session.sessionConfig() != null ? session.sessionConfig().getSystemMessage() : null;
        if (message != null) {
            characters += length(message.getContent());
            if (message.getSections() != null) {
                for (var entry : message.getSections().entrySet()) {
                    characters += length(entry.getKey());
                    if (entry.getValue() != null) characters += length(entry.getValue().getAction()) + length(entry.getValue().getContent());
                }
            }
        }
        return (long) Math.ceil(characters / settings.getEstimatedCharactersPerToken()) + settings.getReservedTokens();
    }

    private static long length(Object value) { return value != null ? String.valueOf(value).length() : 0L; }
}
