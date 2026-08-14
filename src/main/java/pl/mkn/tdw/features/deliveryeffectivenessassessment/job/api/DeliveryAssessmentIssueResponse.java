package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api;

import java.time.Instant;

public record DeliveryAssessmentIssueResponse(
        String issueKey,
        String issueUrl,
        String summary,
        String issueType,
        Instant doneAt
) {
}
