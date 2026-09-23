package pl.mkn.tdw.shared.ai;

public record AnalysisAiUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long totalTokens,
        Double aiCredits,
        long apiDurationMs,
        int apiCallCount,
        String model,
        Long contextTokenLimit,
        Long contextCurrentTokens,
        Long contextMessages
) {
}
