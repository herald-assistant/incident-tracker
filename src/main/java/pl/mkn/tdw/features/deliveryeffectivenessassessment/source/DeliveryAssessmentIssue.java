package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.time.Instant;
import java.util.List;

public record DeliveryAssessmentIssue(
        String issueKey,
        Instant doneAt,
        JiraIssueMaterial material,
        List<String> limitations
) {

    public DeliveryAssessmentIssue {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
