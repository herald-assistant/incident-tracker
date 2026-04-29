package pl.mkn.incidenttracker.analysis.ai.copilot.tools.events;

import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

public sealed interface CopilotToolInvocationEvent permits
        CopilotToolInvocationStartedEvent,
        CopilotToolInvocationFinishedEvent {

    CopilotToolSessionContext sessionContext();

    String sessionId();

    String toolCallId();

    String toolName();

    String rawArguments();
}
