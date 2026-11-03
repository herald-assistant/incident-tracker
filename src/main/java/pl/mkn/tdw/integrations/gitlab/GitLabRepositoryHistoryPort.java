package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabCommit;
import java.time.Instant;
import java.util.Optional;

public interface GitLabRepositoryHistoryPort {
    /** Latest first-parent commit before an exclusive second-precision boundary on a pinned revision. */
    Optional<GitLabCommit> latestBefore(String group, String project, String headCommit, Instant exclusive);
}
