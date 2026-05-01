package pl.mkn.incidenttracker.aiplatform.copilot.tools.events;

import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public sealed interface CopilotToolInvocationEvent permits
        CopilotToolInvocationStartedEvent,
        CopilotToolInvocationFinishedEvent {

    CopilotToolSessionContext sessionContext();

    String sessionId();

    String toolCallId();

    String toolName();

    String rawArguments();
}
