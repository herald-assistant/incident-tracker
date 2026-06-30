package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record CopilotExecutionResult(
        String content,
        AnalysisAiUsage usage,
        String sessionId,
        AnalysisReport report
) {
    public CopilotExecutionResult(
            String content,
            AnalysisAiUsage usage
    ) {
        this(content, usage, null, null);
    }

    public CopilotExecutionResult(
            String content,
            AnalysisAiUsage usage,
            String sessionId
    ) {
        this(content, usage, sessionId, null);
    }
}
