package pl.mkn.tdw.integrations.jira.contract;

import java.time.Instant;

public record JiraIssueTimeTracking(
        Long timeSpentSeconds,
        Long originalEstimateSeconds,
        Long remainingEstimateSeconds,
        Instant capturedAt
) {
}
