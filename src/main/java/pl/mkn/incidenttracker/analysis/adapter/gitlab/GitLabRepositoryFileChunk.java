package pl.mkn.incidenttracker.analysis.adapter.gitlab;

public record GitLabRepositoryFileChunk(
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
