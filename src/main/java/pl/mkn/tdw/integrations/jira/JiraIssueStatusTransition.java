package pl.mkn.tdw.integrations.jira;

import java.time.Instant;

public record JiraIssueStatusTransition(
        Instant changedAt,
        String fromStatusId,
        String fromStatus,
        String toStatusId,
        String toStatus,
        String toStatusCategory
) {
}
