package pl.mkn.tdw.api.aioptions;

public record AnalysisAiModelTokenRates(
        Double input,
        Double cachedInput,
        Double cacheWrite,
        Double output
) {
}
