package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisAiModelOption(
        String id,
        String name,
        boolean supportsReasoningEffort,
        List<String> reasoningEfforts,
        String defaultReasoningEffort
) {

    public AnalysisAiModelOption {
        id = id != null ? id : "";
        name = name != null ? name : "";
        reasoningEfforts = reasoningEfforts != null ? List.copyOf(reasoningEfforts) : List.of();
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
    }
}
