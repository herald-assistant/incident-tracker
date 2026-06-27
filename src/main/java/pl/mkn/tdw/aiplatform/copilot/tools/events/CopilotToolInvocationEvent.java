package pl.mkn.tdw.aiplatform.copilot.tools.events;

import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public sealed interface CopilotToolInvocationEvent permits
        CopilotToolInvocationStartedEvent,
        CopilotToolInvocationFinishedEvent {

    CopilotToolSessionContext sessionContext();

    String sessionId();

    String toolCallId();

    String toolName();

    String rawArguments();
}
