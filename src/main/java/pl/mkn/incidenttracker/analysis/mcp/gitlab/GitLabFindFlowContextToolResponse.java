package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import java.util.List;

public record GitLabFindFlowContextToolResponse(
        String group,
        String branch,
        List<GitLabFlowContextGroup> groups,
        List<String> recommendedNextReads
) {
}
