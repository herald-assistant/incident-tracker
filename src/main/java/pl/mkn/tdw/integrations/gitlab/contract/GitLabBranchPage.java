package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;

public record GitLabBranchPage(List<GitLabBranch> branches, boolean truncated) {
    public GitLabBranchPage {
        branches = branches != null ? List.copyOf(branches) : List.of();
    }
}
