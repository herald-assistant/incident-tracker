package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

public interface CopilotSessionExecutionMetricsRecorder {

    void recordExecutionDurations(
            String copilotSessionId,
            long clientStartDurationMs,
            long createSessionDurationMs,
            long sendAndWaitDurationMs,
            long totalExecutionDurationMs
    );

    void recordAssistantUsage(
            String copilotSessionId,
            String model,
            Double inputTokens,
            Double outputTokens,
            Double cacheReadTokens,
            Double cacheWriteTokens,
            Double cost,
            Double durationMs
    );

    void recordSessionUsageInfo(
            String copilotSessionId,
            double tokenLimit,
            double currentTokens,
            double messagesLength
    );
}
