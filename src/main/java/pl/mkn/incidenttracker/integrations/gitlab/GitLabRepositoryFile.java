package pl.mkn.incidenttracker.integrations.gitlab;

public record GitLabRepositoryFile(
        String group,
        String projectName,
        String branch,
        String filePath
) {
}
