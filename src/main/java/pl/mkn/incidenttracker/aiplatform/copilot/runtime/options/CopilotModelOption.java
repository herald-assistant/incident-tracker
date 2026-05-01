package pl.mkn.incidenttracker.aiplatform.copilot.runtime.options;

import java.util.List;

public record CopilotModelOption(
        String id,
        String name,
        boolean supportsReasoningEffort,
        List<String> reasoningEfforts,
        String defaultReasoningEffort
) {

    public CopilotModelOption {
        id = id != null ? id : "";
        name = name != null ? name : "";
        reasoningEfforts = reasoningEfforts != null ? List.copyOf(reasoningEfforts) : List.of();
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
    }
}
