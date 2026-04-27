package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisAiModelOptionsResponse(
        String defaultModel,
        String defaultReasoningEffort,
        List<String> defaultReasoningEfforts,
        List<AnalysisAiModelOption> models
) {

    public AnalysisAiModelOptionsResponse {
        defaultModel = defaultModel != null ? defaultModel : "";
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
        defaultReasoningEfforts = defaultReasoningEfforts != null
                ? List.copyOf(defaultReasoningEfforts)
                : List.of();
        models = models != null ? List.copyOf(models) : List.of();
    }
}
