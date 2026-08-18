package pl.mkn.tdw.features.deliverycomplexityassessment.source;

import java.util.List;

public record DeliveryAssessmentSourceResult(
        String effectiveJql,
        int jiraTotal,
        boolean truncated,
        List<DeliveryAssessmentIssueSource> issues,
        List<String> limitations
) {

    public DeliveryAssessmentSourceResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
