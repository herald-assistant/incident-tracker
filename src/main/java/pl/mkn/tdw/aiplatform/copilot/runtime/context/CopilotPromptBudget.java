package pl.mkn.tdw.aiplatform.copilot.runtime.context;

public record CopilotPromptBudget(
        long estimatedInputTokens,
        long promptTokenLimit,
        long defaultPromptTokenLimit,
        long maxOutputTokens,
        int reservedTokens,
        double safetyRatio,
        boolean requiresLongContext
) {
    public boolean known() { return promptTokenLimit > 0; }
    public long usableTokens() { return (long) Math.floor(promptTokenLimit * safetyRatio); }
    public boolean fits() { return known() && estimatedInputTokens <= usableTokens(); }
    public CopilotPromptBudget withLimit(long limit) {
        return new CopilotPromptBudget(estimatedInputTokens, limit, Math.min(defaultPromptTokenLimit, limit),
                maxOutputTokens, reservedTokens, safetyRatio, false);
    }
}
