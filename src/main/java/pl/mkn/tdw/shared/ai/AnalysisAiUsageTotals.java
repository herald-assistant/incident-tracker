package pl.mkn.tdw.shared.ai;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public final class AnalysisAiUsageTotals {
    private AnalysisAiUsageTotals() {}

    public static AnalysisAiUsage sum(Collection<AnalysisAiUsage> values) {
        var usages = values.stream().filter(Objects::nonNull).toList();
        if (usages.isEmpty()) return null;
        return new AnalysisAiUsage(usages.stream().mapToLong(AnalysisAiUsage::inputTokens).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::outputTokens).sum(),
                optionalSum(usages, AnalysisAiUsage::cacheReadTokens), optionalSum(usages, AnalysisAiUsage::cacheWriteTokens),
                usages.stream().mapToLong(AnalysisAiUsage::totalTokens).sum(),
                usages.stream().allMatch(u -> u.aiCredits() != null) ? usages.stream().mapToDouble(AnalysisAiUsage::aiCredits).sum() : null,
                usages.stream().mapToLong(AnalysisAiUsage::apiDurationMs).sum(), usages.stream().mapToInt(AnalysisAiUsage::apiCallCount).sum(),
                usages.stream().map(AnalysisAiUsage::model).filter(Objects::nonNull).distinct().reduce((a,b) -> a + ", " + b).orElse(null),
                maximum(usages, AnalysisAiUsage::contextTokenLimit), maximum(usages, AnalysisAiUsage::contextCurrentTokens),
                maximum(usages, AnalysisAiUsage::contextMessages), optionalSum(usages, AnalysisAiUsage::reasoningTokens));
    }
    private static Long optionalSum(Collection<AnalysisAiUsage> values, Function<AnalysisAiUsage, Long> field) {
        return values.stream().allMatch(u -> field.apply(u) != null) ? values.stream().mapToLong(u -> field.apply(u)).sum() : null;
    }
    private static Long maximum(Collection<AnalysisAiUsage> values, Function<AnalysisAiUsage, Long> field) {
        return values.stream().map(field).filter(Objects::nonNull).max(Long::compareTo).orElse(null);
    }
}
