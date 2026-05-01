package pl.mkn.incidenttracker.integrations.gitlab;

public record GitLabRepositoryFileContent(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
