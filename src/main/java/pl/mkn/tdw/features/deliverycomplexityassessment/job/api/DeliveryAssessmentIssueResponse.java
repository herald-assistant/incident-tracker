package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

import java.time.Instant;

public record DeliveryAssessmentIssueResponse(
        String issueKey,
        String issueUrl,
        String summary,
        String issueType,
        Instant doneAt,
        DeliveryAssessmentTeamResponse team,
        Long timeSpentSeconds,
        Long originalEstimateSeconds,
        Long remainingEstimateSeconds,
        Instant timeTrackingCapturedAt
) {

    public DeliveryAssessmentIssueResponse(
            String issueKey,
            String issueUrl,
            String summary,
            String issueType,
            Instant doneAt,
            DeliveryAssessmentTeamResponse team
    ) {
        this(issueKey, issueUrl, summary, issueType, doneAt, team, null, null, null, null);
    }

    public DeliveryAssessmentIssueResponse(
            String issueKey,
            String issueUrl,
            String summary,
            String issueType,
            Instant doneAt
    ) {
        this(issueKey, issueUrl, summary, issueType, doneAt, null, null, null, null, null);
    }
}
