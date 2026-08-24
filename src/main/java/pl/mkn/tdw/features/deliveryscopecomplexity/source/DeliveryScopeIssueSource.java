package pl.mkn.tdw.features.deliveryscopecomplexity.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;

import java.util.List;

public record DeliveryScopeIssueSource(
        DeliveryScopeIssue issue,
        List<GitLabMergeRequest> mergeRequests,
        List<String> limitations
) {

    public DeliveryScopeIssueSource {
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
