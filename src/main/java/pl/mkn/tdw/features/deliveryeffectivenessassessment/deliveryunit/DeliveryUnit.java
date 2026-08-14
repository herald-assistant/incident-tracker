package pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentIssue;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;

import java.util.List;

public record DeliveryUnit(
        String unitId,
        List<DeliveryAssessmentIssue> issues,
        List<GitLabMergeRequest> mergeRequests,
        List<String> limitations
) {

    public DeliveryUnit {
        issues = issues != null ? List.copyOf(issues) : List.of();
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
