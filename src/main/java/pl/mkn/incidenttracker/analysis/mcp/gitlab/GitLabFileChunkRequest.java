package pl.mkn.incidenttracker.analysis.mcp.gitlab;

public record GitLabFileChunkRequest(
        String projectName,
        String filePath,
        int startLine,
        int endLine,
        Integer maxCharacters
) {
}
