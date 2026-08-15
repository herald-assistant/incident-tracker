package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

public record GitLabFrontendRepositoryScope(
        String group,
        String projectName,
        String ref,
        List<String> pathPrefixes
) {

    public GitLabFrontendRepositoryScope {
        group = required(group, "group");
        projectName = required(projectName, "projectName");
        ref = required(ref, "ref");
        var normalizedPrefixes = new LinkedHashSet<String>();
        for (var prefix : pathPrefixes != null ? pathPrefixes : List.<String>of()) {
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            var normalized = prefix.trim().replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.contains("..") || normalized.contains("//")) {
                throw new IllegalArgumentException("pathPrefixes contains an invalid repository path");
            }
            if (StringUtils.hasText(normalized)) {
                normalizedPrefixes.add(normalized);
            }
        }
        pathPrefixes = List.copyOf(normalizedPrefixes);
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

