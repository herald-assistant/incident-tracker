package pl.mkn.tdw.aiplatform.copilot.runtime.options;

public record CopilotModelPricing(
        CopilotModelTokenRates defaultRates,
        CopilotModelTokenRates longContextRates,
        Long longContextThresholdTokens
) {
}
