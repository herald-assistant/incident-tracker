package pl.mkn.tdw.aiplatform.copilot.runtime.context;

public record CopilotEffectiveContextTier(
        String modelId,
        String reasoningEffort,
        String contextTier
) {
}
