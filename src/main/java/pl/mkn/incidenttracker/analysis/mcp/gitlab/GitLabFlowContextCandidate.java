package pl.mkn.incidenttracker.analysis.mcp.gitlab;

public record GitLabFlowContextCandidate(
        String group,
        String projectName,
        String branch,
        String filePath,
        String matchReason,
        int matchScore,
        String inferredRole,
        String recommendedReadStrategy,
        String preview
) {
}
