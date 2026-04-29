package pl.mkn.incidenttracker.analysis.ai;

public record AnalysisAiUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long totalTokens,
        double cost,
        long apiDurationMs,
        int apiCallCount,
        String model,
        Long contextTokenLimit,
        Long contextCurrentTokens,
        Long contextMessages
) {
}
