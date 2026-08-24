package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import java.time.Instant;

public record DeliveryScopeIssueResponse(
        String issueKey,
        String issueUrl,
        String summary,
        String issueType,
        Instant doneAt,
        DeliveryScopeTeamResponse team
) {

    public DeliveryScopeIssueResponse(
            String issueKey,
            String issueUrl,
            String summary,
            String issueType,
            Instant doneAt
    ) {
        this(issueKey, issueUrl, summary, issueType, doneAt, null);
    }
}
