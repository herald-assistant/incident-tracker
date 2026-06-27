package pl.mkn.tdw.common;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class GitLabPathUtils {

    private GitLabPathUtils() {
    }

    public static boolean isSameOrNestedPath(String rootPath, String candidatePath) {
        if (!StringUtils.hasText(rootPath) || !StringUtils.hasText(candidatePath)) {
            return false;
        }

        var normalizedRoot = normalizePath(rootPath);
        var normalizedCandidate = normalizePath(candidatePath);
        return normalizedCandidate.equals(normalizedRoot)
                || normalizedCandidate.startsWith(normalizedRoot + "/");
    }

    public static String relativeProjectPath(String rootGroup, String rawProjectPath) {
        if (!StringUtils.hasText(rawProjectPath)) {
            return null;
        }

        var projectPath = trimSlashes(rawProjectPath.trim());
        if (!StringUtils.hasText(rootGroup)) {
            return projectPath;
        }

        var prefix = trimSlashes(rootGroup.trim()) + "/";
        if (projectPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return projectPath.substring(prefix.length());
        }

        return projectPath;
    }

    public static String trimSlashes(String value) {
        var start = 0;
        var end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String normalizePath(String value) {
        return StringUtils.hasText(value)
                ? trimSlashes(value.trim()).toLowerCase(Locale.ROOT)
                : "";
    }
}
