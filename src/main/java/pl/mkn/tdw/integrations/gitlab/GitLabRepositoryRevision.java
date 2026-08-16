package pl.mkn.tdw.integrations.gitlab;

import org.springframework.util.StringUtils;

public record GitLabRepositoryRevision(
        String group,
        String projectName,
        String ref,
        String commitId,
        String committedAt
) {

    public GitLabRepositoryRevision {
        group = required(group, "group");
        projectName = required(projectName, "projectName");
        ref = required(ref, "ref");
        commitId = required(commitId, "commitId");
        committedAt = normalize(committedAt);
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
