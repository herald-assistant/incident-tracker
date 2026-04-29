package pl.mkn.incidenttracker.analysis.ai.chat;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt
) {
}

