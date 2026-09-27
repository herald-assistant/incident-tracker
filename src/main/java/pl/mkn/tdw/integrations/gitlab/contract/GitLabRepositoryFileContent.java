package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabRepositoryFileContent(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
