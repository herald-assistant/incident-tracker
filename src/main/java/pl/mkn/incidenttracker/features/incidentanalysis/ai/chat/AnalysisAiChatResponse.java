package pl.mkn.incidenttracker.features.incidentanalysis.ai.chat;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt
) {
}

