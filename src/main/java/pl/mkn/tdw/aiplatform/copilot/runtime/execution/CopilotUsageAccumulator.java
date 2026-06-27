package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.LinkedHashSet;
import java.util.Set;

final class CopilotUsageAccumulator {

    private int callCount;
    private double inputTokens;
    private double outputTokens;
    private double cacheReadTokens;
    private double cacheWriteTokens;
    private double cost;
    private double apiDurationMs;
    private final Set<String> models = new LinkedHashSet<>();
    private boolean hasContextUsageInfo;
    private double contextTokenLimit;
    private double contextCurrentTokens;
    private double contextMessages;

    void recordAssistantUsage(
            String model,
            Number inputTokens,
            Number outputTokens,
            Number cacheReadTokens,
            Number cacheWriteTokens,
            Number cost,
            Number durationMs
    ) {
        callCount++;
        this.inputTokens += numeric(inputTokens);
        this.outputTokens += numeric(outputTokens);
        this.cacheReadTokens += numeric(cacheReadTokens);
        this.cacheWriteTokens += numeric(cacheWriteTokens);
        this.cost += numeric(cost);
        this.apiDurationMs += numeric(durationMs);
        if (StringUtils.hasText(model)) {
            models.add(model);
        }
    }

    void recordSessionUsageInfo(
            double tokenLimit,
            double currentTokens,
            double messagesLength
    ) {
        hasContextUsageInfo = true;
        contextTokenLimit = tokenLimit;
        contextCurrentTokens = currentTokens;
        contextMessages = messagesLength;
    }

    AnalysisAiUsage snapshot() {
        if (callCount <= 0) {
            return null;
        }

        var roundedInputTokens = rounded(inputTokens);
        var roundedOutputTokens = rounded(outputTokens);
        return new AnalysisAiUsage(
                roundedInputTokens,
                roundedOutputTokens,
                rounded(cacheReadTokens),
                rounded(cacheWriteTokens),
                roundedInputTokens + roundedOutputTokens,
                cost,
                rounded(apiDurationMs),
                callCount,
                models.isEmpty() ? null : String.join(", ", models),
                hasContextUsageInfo ? rounded(contextTokenLimit) : null,
                hasContextUsageInfo ? rounded(contextCurrentTokens) : null,
                hasContextUsageInfo ? rounded(contextMessages) : null
        );
    }

    private double numeric(Number value) {
        return value != null ? value.doubleValue() : 0D;
    }

    private long rounded(double value) {
        return Math.round(value);
    }
}
