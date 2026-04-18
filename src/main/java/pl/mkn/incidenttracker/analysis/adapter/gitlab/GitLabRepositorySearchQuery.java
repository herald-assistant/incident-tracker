package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import java.util.List;

public record GitLabRepositorySearchQuery(
        String correlationId,
        String group,
        String branch,
        List<String> projectNames,
        List<String> operationNames,
        List<String> keywords
) {
}
