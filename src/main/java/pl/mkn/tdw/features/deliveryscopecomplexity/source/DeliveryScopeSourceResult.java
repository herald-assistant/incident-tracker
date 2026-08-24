package pl.mkn.tdw.features.deliveryscopecomplexity.source;

import java.util.List;

public record DeliveryScopeSourceResult(
        String effectiveJql,
        int jiraTotal,
        boolean truncated,
        List<DeliveryScopeIssueSource> issues,
        List<String> limitations
) {

    public DeliveryScopeSourceResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
