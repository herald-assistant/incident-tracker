package pl.mkn.incidenttracker.analysis.mcp.gitlab;

public record GitLabReadRepositoryFileChunkToolResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        int requestedStartLine,
        int requestedEndLine,
        int returnedStartLine,
        int returnedEndLine,
        int totalLines,
        String content,
        boolean truncated
) {
}
