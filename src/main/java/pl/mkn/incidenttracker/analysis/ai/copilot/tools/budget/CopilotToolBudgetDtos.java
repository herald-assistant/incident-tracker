package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CopilotToolBudgetDtos {

    private CopilotToolBudgetDtos() {
    }

    public record Decision(
            String sessionId,
            String toolName,
            boolean denied,
            boolean softLimitExceeded,
            String reason,
            List<String> warnings
    ) {

        public Decision {
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }

        public static Decision allowed(String sessionId, String toolName) {
            return new Decision(sessionId, toolName, false, false, null, List.of());
        }

        public static Decision soft(
                String sessionId,
                String toolName,
                List<String> warnings
        ) {
            return new Decision(
                    sessionId,
                    toolName,
                    false,
                    true,
                    warnings != null && !warnings.isEmpty() ? warnings.get(0) : null,
                    warnings
            );
        }

        public static Decision denied(
                String sessionId,
                String toolName,
                List<String> warnings
        ) {
            return new Decision(
                    sessionId,
                    toolName,
                    true,
                    false,
                    warnings != null && !warnings.isEmpty() ? warnings.get(0) : "Tool budget limit exceeded.",
                    warnings
            );
        }
    }

    public static Map<String, Object> deniedResult(Decision decision) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("status", "denied_by_tool_budget");
        payload.put("toolName", decision.toolName());
        payload.put("reason", decision.reason());
        payload.put(
                "instruction",
                "Stop further exploration and return the best grounded analysis with visibility limits."
        );
        payload.put("warnings", decision.warnings());
        return payload;
    }
}
