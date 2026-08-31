package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import java.time.Instant;

public record DeliveryScopeIssueResponse(
        String issueKey,
        String issueUrl,
        String summary,
        String issueType,
        Instant doneAt,
        DeliveryScopeTeamResponse team,
        Long timeSpentSeconds,
        Long originalEstimateSeconds,
        Long remainingEstimateSeconds,
        Instant timeTrackingCapturedAt
) {

    public DeliveryScopeIssueResponse(
            String issueKey,
            String issueUrl,
            String summary,
            String issueType,
            Instant doneAt,
            DeliveryScopeTeamResponse team
    ) {
        this(issueKey, issueUrl, summary, issueType, doneAt, team, null, null, null, null);
    }

    public DeliveryScopeIssueResponse(
            String issueKey,
            String issueUrl,
            String summary,
            String issueType,
            Instant doneAt
    ) {
        this(issueKey, issueUrl, summary, issueType, doneAt, null, null, null, null, null);
    }
}
