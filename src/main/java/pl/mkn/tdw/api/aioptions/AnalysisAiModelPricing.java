package pl.mkn.tdw.api.aioptions;

public record AnalysisAiModelPricing(
        AnalysisAiModelTokenRates defaultRates,
        AnalysisAiModelTokenRates longContextRates,
        Long longContextThresholdTokens
) {
}
