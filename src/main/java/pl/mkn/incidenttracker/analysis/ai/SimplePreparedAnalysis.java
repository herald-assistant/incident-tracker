package pl.mkn.incidenttracker.analysis.ai;

public record SimplePreparedAnalysis(
        String providerName,
        String correlationId,
        String prompt,
        AnalysisAiAnalysisRequest request
) implements AnalysisAiPreparedAnalysis {

    public SimplePreparedAnalysis(String providerName, String correlationId, String prompt) {
        this(providerName, correlationId, prompt, null);
    }
}
