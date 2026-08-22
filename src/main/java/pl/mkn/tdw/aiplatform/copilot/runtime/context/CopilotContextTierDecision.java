package pl.mkn.tdw.aiplatform.copilot.runtime.context;

public record CopilotContextTierDecision(
        boolean policyEnabled,
        boolean modelSupported,
        String modelId,
        long defaultWindowTokens,
        long longContextWindowTokens,
        long estimatedInitialTokens,
        long initialThresholdTokens,
        boolean useLongContextInitially,
        String reason
) {
}
