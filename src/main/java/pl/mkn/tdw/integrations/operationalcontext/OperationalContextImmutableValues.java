package pl.mkn.tdw.integrations.operationalcontext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OperationalContextImmutableValues {

    private OperationalContextImmutableValues() {
    }

    static Map<String, Object> copyMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    static Map<String, Map<String, Object>> copyDocuments(Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        var copy = new LinkedHashMap<String, Map<String, Object>>();
        source.forEach((name, document) -> copy.put(name, copyMap(document)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            var copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(copyValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Object[] array) {
            var copy = new ArrayList<>();
            for (var item : array) {
                copy.add(copyValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof Enum<?>) {
            return value;
        }
        throw new IllegalArgumentException(
                "Unsupported mutable operational-context value type: " + value.getClass().getName()
        );
    }
}
