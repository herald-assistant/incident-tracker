package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CopilotUsageAccumulator {

    /** Allows a feature to checkpoint the same public usage events before a run finishes. */
    public void recordActivity(pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent event) {
        var details=event.details();
        if("assistant.usage".equals(event.type()) && details.containsKey("inputTokens")) {
            recordAssistantUsage(java.util.Objects.toString(details.get("model"),null),number(details.get("inputTokens")),
                    number(details.get("outputTokens")),number(details.get("cacheReadTokens")),number(details.get("cacheWriteTokens")),
                    number(details.get("reasoningTokens")),number(details.get("totalNanoAiu")),number(details.get("durationMs")));
        } else if("session.usage_info".equals(event.type())) {
            recordSessionUsageInfo(numeric(number(details.get("tokenLimit"))),numeric(number(details.get("currentTokens"))),
                    numeric(number(details.get("messagesLength"))));
        }
    }
    private Number number(Object value) {return value instanceof Number n?n:null;}

    private int callCount;
    private double inputTokens;
    private double outputTokens;
    private double cacheReadTokens;
    private double cacheWriteTokens;
    private boolean hasCompleteCacheReadTokens = true;
    private boolean hasCompleteCacheWriteTokens = true;
    private double reasoningTokens;
    private boolean hasCompleteReasoningTokens = true;
    private double nanoAiu;
    private boolean hasCompleteAiCredits = true;
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
            Number reasoningTokens,
            Number totalNanoAiu,
            Number durationMs
    ) {
        callCount++;
        this.inputTokens += numeric(inputTokens);
        this.outputTokens += numeric(outputTokens);
        if (cacheReadTokens == null || !Double.isFinite(cacheReadTokens.doubleValue()) || cacheReadTokens.doubleValue() < 0) {
            hasCompleteCacheReadTokens = false;
        } else {
            this.cacheReadTokens += cacheReadTokens.doubleValue();
        }
        if (cacheWriteTokens == null || !Double.isFinite(cacheWriteTokens.doubleValue()) || cacheWriteTokens.doubleValue() < 0) {
            hasCompleteCacheWriteTokens = false;
        } else {
            this.cacheWriteTokens += cacheWriteTokens.doubleValue();
        }
        if (reasoningTokens == null || !Double.isFinite(reasoningTokens.doubleValue()) || reasoningTokens.doubleValue() < 0) {
            hasCompleteReasoningTokens = false;
        } else {
            this.reasoningTokens += reasoningTokens.doubleValue();
        }
        if (totalNanoAiu == null || !Double.isFinite(totalNanoAiu.doubleValue()) || totalNanoAiu.doubleValue() < 0) {
            hasCompleteAiCredits = false;
        } else {
            nanoAiu += totalNanoAiu.doubleValue();
        }
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

    public AnalysisAiUsage snapshot() {
        if (callCount <= 0) {
            return null;
        }

        var roundedInputTokens = rounded(inputTokens);
        var roundedOutputTokens = rounded(outputTokens);
        return new AnalysisAiUsage(
                roundedInputTokens,
                roundedOutputTokens,
                hasCompleteCacheReadTokens ? rounded(cacheReadTokens) : null,
                hasCompleteCacheWriteTokens ? rounded(cacheWriteTokens) : null,
                roundedInputTokens + roundedOutputTokens,
                hasCompleteAiCredits ? nanoAiu / 1_000_000_000D : null,
                rounded(apiDurationMs),
                callCount,
                models.isEmpty() ? null : String.join(", ", models),
                hasContextUsageInfo ? rounded(contextTokenLimit) : null,
                hasContextUsageInfo ? rounded(contextCurrentTokens) : null,
                hasContextUsageInfo ? rounded(contextMessages) : null,
                hasCompleteReasoningTokens ? rounded(reasoningTokens) : null
        );
    }

    private double numeric(Number value) {
        return value != null ? value.doubleValue() : 0D;
    }

    private long rounded(double value) {
        return Math.round(value);
    }
}
