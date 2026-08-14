package pl.mkn.tdw.integrations.jira;

import java.util.List;

public record JiraIssueSearchResult(
        String effectiveJql,
        int total,
        boolean truncated,
        List<JiraIssueSearchItem> issues,
        List<String> limitations
) {

    public JiraIssueSearchResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
