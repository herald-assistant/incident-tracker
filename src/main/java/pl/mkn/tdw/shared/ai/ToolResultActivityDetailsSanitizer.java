package pl.mkn.tdw.shared.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolResultActivityDetailsSanitizer {

    private static final Set<String> PUBLIC_FIELDS = Set.of(
            "success",
            "resultContent",
            "resultDetailedContent"
    );
    private static final Set<String> RESULT_FIELDS = Set.of(
            "format",
            "value",
            "truncated",
            "originalCharacters",
            "retainedCharacters",
            "omittedEntries",
            "truncatedStrings"
    );
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 2_000;
    private static final int MAX_STRING_LENGTH = 12_000;

    private ToolResultActivityDetailsSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        var safe = new LinkedHashMap<String, Object>();
        details.forEach((name, value) -> {
            if (!PUBLIC_FIELDS.contains(name)) {
                return;
            }
            if ("success".equals(name) && value instanceof Boolean) {
                safe.put(name, value);
                return;
            }
            if (isSafeResult(value)) {
                safe.put(name, value);
            }
        });
        return Map.copyOf(safe);
    }

    private static boolean isSafeResult(Object value) {
        if (value instanceof AnalysisAiToolResultContent result) {
            return result.format() != null
                    && nonNegative(result.originalCharacters())
                    && nonNegative(result.retainedCharacters())
                    && nonNegative(result.omittedEntries())
                    && nonNegative(result.truncatedStrings())
                    && isSafeValue(result.value(), 0, new Counter());
        }
        if (!(value instanceof Map<?, ?> result) || !result.keySet().stream().allMatch(RESULT_FIELDS::contains)) {
            return false;
        }
        var format = result.get("format");
        return ("JSON".equals(format) || "TEXT".equals(format))
                && result.get("truncated") instanceof Boolean
                && nonNegativeNumber(result.get("originalCharacters"))
                && nonNegativeNumber(result.get("retainedCharacters"))
                && nonNegativeNumber(result.get("omittedEntries"))
                && nonNegativeNumber(result.get("truncatedStrings"))
                && isSafeValue(result.get("value"), 0, new Counter());
    }

    private static boolean isSafeValue(Object value, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.nodes > MAX_NODES) {
            return false;
        }
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return true;
        }
        if (value instanceof String string) {
            return string.length() <= MAX_STRING_LENGTH;
        }
        if (value instanceof List<?> list) {
            return list.stream().allMatch(item -> isSafeValue(item, depth + 1, counter));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().allMatch(entry -> entry.getKey() instanceof String key
                    && key.length() <= 500
                    && isSafeValue(entry.getValue(), depth + 1, counter));
        }
        return false;
    }

    private static boolean nonNegative(int value) {
        return value >= 0;
    }

    private static boolean nonNegativeNumber(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= 0;
    }

    private static final class Counter {
        private int nodes;
    }
}
