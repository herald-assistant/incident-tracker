package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

public record GitLabRepositorySearchQuery(
        String correlationId,
        String group,
        String branch,
        List<String> projectNames,
        List<String> operationNames,
        List<String> keywords,
        List<String> pathPrefixes
) {
}
