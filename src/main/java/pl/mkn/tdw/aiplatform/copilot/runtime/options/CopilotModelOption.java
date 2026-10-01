package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import java.util.List;

public record CopilotModelOption(
        String id,
        String name,
        boolean supportsReasoningEffort,
        List<String> reasoningEfforts,
        String defaultReasoningEffort,
        long defaultContextWindowTokens,
        long longContextWindowTokens,
        String modelPickerCategory,
        CopilotModelPricing pricing,
        long defaultPromptTokens,
        long longContextPromptTokens
) {

    public CopilotModelOption(String id, String name, boolean supportsReasoningEffort,
                              List<String> reasoningEfforts, String defaultReasoningEffort,
                              long defaultContextWindowTokens, long longContextWindowTokens) {
        this(id, name, supportsReasoningEffort, reasoningEfforts, defaultReasoningEffort,
                defaultContextWindowTokens, longContextWindowTokens, "", null);
    }

    public CopilotModelOption(String id, String name, boolean supportsReasoningEffort,
                              List<String> reasoningEfforts, String defaultReasoningEffort,
                              long defaultContextWindowTokens, long longContextWindowTokens,
                              String modelPickerCategory, CopilotModelPricing pricing) {
        this(id, name, supportsReasoningEffort, reasoningEfforts, defaultReasoningEffort,
                defaultContextWindowTokens, longContextWindowTokens, modelPickerCategory, pricing, 0L, 0L);
    }

    public CopilotModelOption {
        id = id != null ? id : "";
        name = name != null ? name : "";
        reasoningEfforts = reasoningEfforts != null ? List.copyOf(reasoningEfforts) : List.of();
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
        defaultContextWindowTokens = Math.max(defaultContextWindowTokens, 0L);
        longContextWindowTokens = Math.max(longContextWindowTokens, 0L);
        defaultPromptTokens = Math.max(defaultPromptTokens, 0L);
        longContextPromptTokens = Math.max(longContextPromptTokens, 0L);
        modelPickerCategory = modelPickerCategory != null ? modelPickerCategory : "";
    }

    public boolean supportsLongContext() {
        return defaultContextWindowTokens > 0L
                && longContextWindowTokens > defaultContextWindowTokens;
    }
}
