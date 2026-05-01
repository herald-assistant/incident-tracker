package pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy;

import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record CopilotToolInvocationPolicyRequest(
        CopilotToolSessionContext sessionContext,
        String sessionId,
        String toolCallId,
        String toolName,
        String rawArguments
) {
}
