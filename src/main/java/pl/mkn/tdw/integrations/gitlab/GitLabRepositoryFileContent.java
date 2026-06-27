package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryFileContent(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
