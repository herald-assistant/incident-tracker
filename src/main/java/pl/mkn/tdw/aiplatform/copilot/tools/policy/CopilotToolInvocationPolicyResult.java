package pl.mkn.tdw.aiplatform.copilot.tools.policy;

import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;

public record CopilotToolInvocationPolicyResult(
        CopilotToolSessionContext sessionContext,
        String sessionId,
        String toolCallId,
        String toolName,
        String rawArguments,
        String rawResult
) {
}
