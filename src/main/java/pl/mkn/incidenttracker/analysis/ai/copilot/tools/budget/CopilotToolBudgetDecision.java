package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import java.util.List;

public record CopilotToolBudgetDecision(
        String sessionId,
        String toolName,
        boolean denied,
        boolean softLimitExceeded,
        String reason,
        List<String> warnings
) {

    public CopilotToolBudgetDecision {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public static CopilotToolBudgetDecision allowed(String sessionId, String toolName) {
        return new CopilotToolBudgetDecision(sessionId, toolName, false, false, null, List.of());
    }

    public static CopilotToolBudgetDecision soft(
            String sessionId,
            String toolName,
            List<String> warnings
    ) {
        return new CopilotToolBudgetDecision(
                sessionId,
                toolName,
                false,
                true,
                warnings != null && !warnings.isEmpty() ? warnings.get(0) : null,
                warnings
        );
    }

    public static CopilotToolBudgetDecision denied(
            String sessionId,
            String toolName,
            List<String> warnings
    ) {
        return new CopilotToolBudgetDecision(
                sessionId,
                toolName,
                true,
                false,
                warnings != null && !warnings.isEmpty() ? warnings.get(0) : "Tool budget limit exceeded.",
                warnings
        );
    }
}
