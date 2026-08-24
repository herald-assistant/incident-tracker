package pl.mkn.tdw.features.deliveryscopecomplexity.source;

import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.time.Instant;
import java.util.List;

public record DeliveryScopeIssue(
        String issueKey,
        Instant doneAt,
        JiraIssueMaterial material,
        DeliveryScopeTeam team,
        List<String> limitations
) {

    public DeliveryScopeIssue {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public DeliveryScopeIssue(
            String issueKey,
            Instant doneAt,
            JiraIssueMaterial material,
            List<String> limitations
    ) {
        this(issueKey, doneAt, material, null, limitations);
    }
}
