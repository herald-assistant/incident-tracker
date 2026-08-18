package pl.mkn.tdw.features.deliverycomplexityassessment.source;

import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.time.Instant;
import java.util.List;

public record DeliveryAssessmentIssue(
        String issueKey,
        Instant doneAt,
        JiraIssueMaterial material,
        DeliveryAssessmentTeam team,
        List<String> limitations
) {

    public DeliveryAssessmentIssue {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public DeliveryAssessmentIssue(
            String issueKey,
            Instant doneAt,
            JiraIssueMaterial material,
            List<String> limitations
    ) {
        this(issueKey, doneAt, material, null, limitations);
    }
}
