package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryFile(
        String group,
        String projectName,
        String branch,
        String filePath
) {
}
