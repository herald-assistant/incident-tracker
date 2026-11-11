package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff;

/** End of pagination does not prove completeness beyond GitLab's server diff limits. */
public record GitLabCommitDiffPage(List<FileDiff> files, Integer nextPage) {
    public GitLabCommitDiffPage { files = List.copyOf(files); }
}
