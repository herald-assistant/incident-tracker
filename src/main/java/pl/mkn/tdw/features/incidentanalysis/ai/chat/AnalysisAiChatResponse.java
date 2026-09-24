package pl.mkn.tdw.features.incidentanalysis.ai.chat;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record AnalysisAiChatResponse(
        String providerName,
        String content,
        String prompt,
        String copilotSessionId,
        pl.mkn.tdw.shared.ai.AnalysisAiUsage usage,
        AnalysisReport report
) {
    public AnalysisAiChatResponse(String providerName, String content, String prompt,
                                  String copilotSessionId, pl.mkn.tdw.shared.ai.AnalysisAiUsage usage) {
        this(providerName, content, prompt, copilotSessionId, usage, null);
    }
    public AnalysisAiChatResponse(
            String providerName,
            String content,
            String prompt
    ) {
        this(providerName, content, prompt, null, null, null);
    }

    public AnalysisAiChatResponse(
            String providerName,
            String content,
            String prompt,
            String copilotSessionId
    ) {
        this(providerName, content, prompt, copilotSessionId, null, null);
    }
}

