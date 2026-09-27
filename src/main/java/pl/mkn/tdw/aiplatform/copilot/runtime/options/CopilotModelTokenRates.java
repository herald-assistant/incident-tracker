package pl.mkn.tdw.aiplatform.copilot.runtime.options;

public record CopilotModelTokenRates(
        Double input,
        Double cachedInput,
        Double cacheWrite,
        Double output
) {
}
