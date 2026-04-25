package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import java.util.List;

public record GitLabFindClassReferencesToolResponse(
        String group,
        String branch,
        String searchedClass,
        List<String> searchKeywords,
        List<GitLabFlowContextGroup> groups,
        List<String> recommendedNextReads
) {
}
