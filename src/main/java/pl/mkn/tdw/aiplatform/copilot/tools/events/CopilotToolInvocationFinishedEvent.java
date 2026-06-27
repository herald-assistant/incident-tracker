package pl.mkn.tdw.aiplatform.copilot.tools.events;

import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record CopilotToolInvocationFinishedEvent(
        CopilotToolSessionContext sessionContext,
        String sessionId,
        String toolCallId,
        String toolName,
        String rawArguments,
        CopilotToolInvocationOutcome outcome,
        String rawResult,
        long latencyMs,
        Throwable exception
) implements CopilotToolInvocationEvent {
}
