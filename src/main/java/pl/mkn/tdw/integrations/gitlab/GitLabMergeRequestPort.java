package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;

public interface GitLabMergeRequestPort {

    GitLabMergeRequestSearchResult findMergeRequestsByIssueKey(String group, String issueKey, int maxResults);
}
