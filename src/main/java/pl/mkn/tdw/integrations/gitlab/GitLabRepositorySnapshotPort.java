package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySnapshot;

/** Complete immutable tree at a verified commit; never returns a truncated result. */
public interface GitLabRepositorySnapshotPort {
    GitLabRepositorySnapshot snapshot(String group, String project, String commitId);
}

