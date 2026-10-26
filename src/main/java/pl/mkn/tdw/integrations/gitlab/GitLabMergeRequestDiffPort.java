package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;

public interface GitLabMergeRequestDiffPort {
    GitLabMergeRequestDiffSnapshot readMergeRequestDiff(String group, String projectName, long mergeRequestIid);
}
