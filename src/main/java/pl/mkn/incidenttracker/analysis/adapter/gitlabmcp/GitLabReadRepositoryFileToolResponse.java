package pl.mkn.incidenttracker.analysis.adapter.gitlabmcp;

public record GitLabReadRepositoryFileToolResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
