package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;

import java.util.List;

public record DeliveryAssessmentIssueSource(
        DeliveryAssessmentIssue issue,
        List<GitLabMergeRequest> mergeRequests,
        List<String> limitations
) {

    public DeliveryAssessmentIssueSource {
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
