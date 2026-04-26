package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CopilotToolBudgetExceededResult {

    private CopilotToolBudgetExceededResult() {
    }

    public static Map<String, Object> from(CopilotToolBudgetDecision decision) {
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
