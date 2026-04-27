package pl.mkn.incidenttracker.analysis.ai;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt
) {
}
