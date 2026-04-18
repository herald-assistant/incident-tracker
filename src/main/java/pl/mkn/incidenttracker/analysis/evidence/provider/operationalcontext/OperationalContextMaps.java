package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OperationalContextMaps {

    private OperationalContextMaps() {
    }

    static String text(Map<String, Object> source, String path) {
        return text(value(source, path));
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }

        var rendered = String.valueOf(value).trim();
        return StringUtils.hasText(rendered) ? rendered : null;
    }

    static List<String> textList(Map<String, Object> source, String path) {
        return textList(value(source, path));
    }

    static List<String> textList(Object value) {
        if (value == null) {
            return List.of();
        }

        if (value instanceof Iterable<?> iterable) {
            var values = new ArrayList<String>();
            for (var item : iterable) {
                var text = text(item);
                if (text != null) {
                    values.add(text);
                }
            }
            return List.copyOf(values);
        }

        var singleValue = text(value);
        return singleValue != null ? List.of(singleValue) : List.of();
    }

    static List<Map<String, Object>> mapList(Map<String, Object> source, String path) {
        return mapList(value(source, path));
    }

    static List<Map<String, Object>> mapList(Object value) {
        if (value == null) {
            return List.of();
        }

        if (value instanceof Iterable<?> iterable) {
            var values = new ArrayList<Map<String, Object>>();
            for (var item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    values.add(copyAsStringKeyMap(map));
                }
            }
            return List.copyOf(values);
        }

        if (value instanceof Map<?, ?> map) {
            return List.of(copyAsStringKeyMap(map));
        }

        return List.of();
    }

    static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Object value(Map<String, Object> source, String path) {
        Object current = source;

        for (var segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private static Map<String, Object> copyAsStringKeyMap(Map<?, ?> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

}
