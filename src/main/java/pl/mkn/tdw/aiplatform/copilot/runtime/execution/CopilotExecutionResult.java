package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record CopilotExecutionResult(
        String content,
        AnalysisAiUsage usage,
        String sessionId
) {
    public CopilotExecutionResult(
            String content,
            AnalysisAiUsage usage
    ) {
        this(content, usage, null);
    }
}
