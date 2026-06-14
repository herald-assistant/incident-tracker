package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class GitLabEndpointUseCaseModelSupport {

    private GitLabEndpointUseCaseModelSupport() {
    }

    static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(GitLabEndpointUseCaseModelSupport::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeHttpMethod(String value) {
        var trimmed = trimToNull(value);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    static String normalizeEndpointPath(String value) {
        var trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    static String normalizeFilePath(String value) {
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

    static String normalizeSourcePathPrefix(String value) {
        var normalized = normalizeFilePath(value);
        if (normalized == null) {
            return GitLabEndpointUseCaseContextRequest.DEFAULT_SOURCE_PATH_PREFIX;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank()
                ? GitLabEndpointUseCaseContextRequest.DEFAULT_SOURCE_PATH_PREFIX
                : normalized;
    }

    static Integer normalizeLimit(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    static int normalizePriority(int value) {
        return Math.max(1, value);
    }
}
