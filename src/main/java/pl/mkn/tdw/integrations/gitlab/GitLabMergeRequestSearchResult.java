package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

public record GitLabMergeRequestSearchResult(
        String issueKey,
        String group,
        List<GitLabMergeRequest> mergeRequests,
        List<String> limitations
) {

    public GitLabMergeRequestSearchResult {
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
