package pl.mkn.tdw.aiplatform.copilot.tools.policy.budget;

import java.util.HashMap;
import java.util.Map;

/** A run-owned hard cap checked before any registered tool callback is invoked. */
public final class CopilotSessionHardToolBudget {

    private final int maxTotalCalls;
    private final Map<String, Integer> maxCallsByTool;
    private final Map<String, Integer> callsByTool = new HashMap<>();
    private int totalCalls;

    public CopilotSessionHardToolBudget(int maxTotalCalls, Map<String, Integer> maxCallsByTool) {
        if (maxTotalCalls < 1 || maxCallsByTool == null || maxCallsByTool.isEmpty()
                || maxCallsByTool.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getKey().isBlank()
                                || entry.getValue() == null || entry.getValue() < 1)) {
            throw new IllegalArgumentException("A positive total and per-tool hard budget are required.");
        }
        this.maxTotalCalls = maxTotalCalls;
        this.maxCallsByTool = Map.copyOf(maxCallsByTool);
    }

    public synchronized String acquireOrDenial(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool is outside the session hard budget.";
        }
        var maxCalls = maxCallsByTool.get(toolName);
        if (maxCalls == null) {
            return "Tool is outside the session hard budget.";
        }
        if (totalCalls >= maxTotalCalls) {
            return "Session hard total tool call budget exceeded.";
        }
        if (callsByTool.getOrDefault(toolName, 0) >= maxCalls) {
            return "Session hard tool call budget exceeded for " + toolName + ".";
        }
        totalCalls++;
        callsByTool.merge(toolName, 1, Integer::sum);
        return null;
    }
}
