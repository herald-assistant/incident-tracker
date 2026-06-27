package pl.mkn.tdw.aiplatform.copilot.tools.events;

import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record CopilotToolInvocationStartedEvent(
        CopilotToolSessionContext sessionContext,
        String sessionId,
        String toolCallId,
        String toolName,
        String rawArguments
) implements CopilotToolInvocationEvent {
}
