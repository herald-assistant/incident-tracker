package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import java.util.List;

public record CopilotModelOption(
        String id,
        String name,
        boolean supportsReasoningEffort,
        List<String> reasoningEfforts,
        String defaultReasoningEffort,
        long defaultContextWindowTokens,
        long longContextWindowTokens
) {

    public CopilotModelOption {
        id = id != null ? id : "";
        name = name != null ? name : "";
        reasoningEfforts = reasoningEfforts != null ? List.copyOf(reasoningEfforts) : List.of();
        defaultReasoningEffort = defaultReasoningEffort != null ? defaultReasoningEffort : "";
        defaultContextWindowTokens = Math.max(defaultContextWindowTokens, 0L);
        longContextWindowTokens = Math.max(longContextWindowTokens, 0L);
    }

    public boolean supportsLongContext() {
        return defaultContextWindowTokens > 0L
                && longContextWindowTokens > defaultContextWindowTokens;
    }
}
