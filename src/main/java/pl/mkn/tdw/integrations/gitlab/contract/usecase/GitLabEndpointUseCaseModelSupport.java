package pl.mkn.tdw.integrations.gitlab.contract.usecase;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GitLabEndpointUseCaseModelSupport {

    private GitLabEndpointUseCaseModelSupport() {
    }

    public static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(GitLabEndpointUseCaseModelSupport::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeHttpMethod(String value) {
        var trimmed = trimToNull(value);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    public static String normalizeEndpointPath(String value) {
        var trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    public static String normalizeFilePath(String value) {
        var trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        var normalized = trimmed.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public static Integer normalizeLimit(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    public static int normalizePriority(int value) {
        return Math.max(1, value);
    }
}
