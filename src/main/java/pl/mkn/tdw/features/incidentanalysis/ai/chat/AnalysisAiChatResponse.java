package pl.mkn.tdw.features.incidentanalysis.ai.chat;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt,
        String copilotSessionId
) {
    public AnalysisAiChatResponse(
            String providerName,
            String content,
            String prompt
    ) {
        this(providerName, content, prompt, null);
    }
}

