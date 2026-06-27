package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;

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
