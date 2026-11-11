package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.List;

public record GitLabCommitPage(List<GitLabHistoryCommit> commits, Integer nextPage) {
    public GitLabCommitPage { commits = List.copyOf(commits); }
}
