package pl.mkn.tdw.features.incidentanalysis.ai.chat;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt,
        String copilotSessionId,
        pl.mkn.tdw.shared.ai.AnalysisAiUsage usage
) {
    public AnalysisAiChatResponse(
            String providerName,
            String content,
            String prompt
    ) {
        this(providerName, content, prompt, null, null);
    }

    public AnalysisAiChatResponse(
            String providerName,
            String content,
            String prompt,
            String copilotSessionId
    ) {
        this(providerName, content, prompt, copilotSessionId, null);
    }
}

