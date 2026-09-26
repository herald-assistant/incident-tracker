package pl.mkn.tdw.integrations.jira.contract;

import java.time.Instant;

public record JiraIssueSearchItem(
        String issueKey,
        String status,
        String statusCategory,
        Instant resolvedAt
) {
}
