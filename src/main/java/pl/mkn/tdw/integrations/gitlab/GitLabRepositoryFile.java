package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryFile(
        String group,
        String projectName,
        String branch,
        String filePath
) {
}
