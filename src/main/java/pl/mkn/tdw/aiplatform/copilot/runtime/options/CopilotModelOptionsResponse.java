package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import java.util.List;

public record CopilotModelOptionsResponse(
        String defaultModel,
        String defaultReasoningEffort,
        List<String> defaultReasoningEfforts,
        List<CopilotModelOption> models
) {

    public CopilotModelOptionsResponse {
        defaultModel = defaultModel != null ? defaultModel : "";
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
        defaultReasoningEfforts = defaultReasoningEfforts != null
                ? List.copyOf(defaultReasoningEfforts)
                : List.of();
        models = models != null ? List.copyOf(models) : List.of();
    }
}
