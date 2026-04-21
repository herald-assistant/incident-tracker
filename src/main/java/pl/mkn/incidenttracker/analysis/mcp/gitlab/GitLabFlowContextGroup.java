package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import java.util.List;

public record GitLabFlowContextGroup(
        String role,
        List<GitLabFlowContextCandidate> candidates
) {
}
