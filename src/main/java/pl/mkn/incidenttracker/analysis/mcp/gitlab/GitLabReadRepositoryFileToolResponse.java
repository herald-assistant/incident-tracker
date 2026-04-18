package pl.mkn.incidenttracker.analysis.mcp.gitlab;

public record GitLabReadRepositoryFileToolResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        String content,
        boolean truncated
) {
}
