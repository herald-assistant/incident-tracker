package pl.mkn.tdw.aiplatform.copilot.tools.policy;

import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record CopilotToolInvocationPolicyRequest(
        CopilotToolSessionContext sessionContext,
        String sessionId,
        String toolCallId,
        String toolName,
        String rawArguments
) {
}
