package pl.mkn.tdw.aiplatform.copilot.runtime.context;

public record CopilotContextTierDecision(
        boolean policyEnabled,
        CopilotContextTierPreference preference,
        boolean modelMetadataAvailable,
        String modelId,
        long defaultWindowTokens,
        long longContextWindowTokens,
        long estimatedInitialTokens,
        long initialThresholdTokens,
        double runtimeUsageThreshold,
        long switchTimeoutMillis,
        boolean useLongContextInitially,
        String reason
) {
}
