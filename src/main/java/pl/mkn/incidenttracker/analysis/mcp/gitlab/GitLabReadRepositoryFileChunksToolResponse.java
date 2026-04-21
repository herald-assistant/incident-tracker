package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import java.util.List;

public record GitLabReadRepositoryFileChunksToolResponse(
        String group,
        String branch,
        List<GitLabFileChunkResult> chunks,
        boolean chunkCountTruncated,
        boolean totalCharacterLimitReached
) {
}
