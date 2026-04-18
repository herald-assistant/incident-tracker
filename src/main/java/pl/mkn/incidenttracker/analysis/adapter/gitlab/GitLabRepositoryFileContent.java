package pl.mkn.incidenttracker.analysis.adapter.gitlab;

public record GitLabRepositoryFileContent(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
