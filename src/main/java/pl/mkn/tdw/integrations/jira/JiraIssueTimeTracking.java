package pl.mkn.tdw.integrations.jira;

import java.time.Instant;

public record JiraIssueTimeTracking(
        Long timeSpentSeconds,
        Long originalEstimateSeconds,
        Long remainingEstimateSeconds,
        Instant capturedAt
) {
}
