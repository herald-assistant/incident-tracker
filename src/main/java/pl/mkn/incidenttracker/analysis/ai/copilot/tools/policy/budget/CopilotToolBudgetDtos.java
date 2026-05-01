package pl.mkn.incidenttracker.analysis.ai.copilot.tools.policy.budget;

import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetDecision;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CopilotToolBudgetDtos {

    private CopilotToolBudgetDtos() {
    }

    public static Map<String, Object> deniedResult(CopilotToolBudgetDecision decision) {
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
