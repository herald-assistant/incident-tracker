package pl.mkn.tdw.api.aioptions;

import java.util.List;

public record AnalysisAiModelOption(
        String id,
        String name,
        boolean supportsReasoningEffort,
        List<String> reasoningEfforts,
        String defaultReasoningEffort,
        String modelPickerCategory,
        AnalysisAiModelPricing pricing
) {

    public AnalysisAiModelOption(String id, String name, boolean supportsReasoningEffort,
                                 List<String> reasoningEfforts, String defaultReasoningEffort) {
        this(id, name, supportsReasoningEffort, reasoningEfforts, defaultReasoningEffort,
                "", null);
    }

    public AnalysisAiModelOption {
        id = id != null ? id : "";
        name = name != null ? name : "";
        reasoningEfforts = reasoningEfforts != null ? List.copyOf(reasoningEfforts) : List.of();
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
        modelPickerCategory = modelPickerCategory != null ? modelPickerCategory : "";
    }
}
