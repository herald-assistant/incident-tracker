package pl.mkn.tdw.integrations.gitlab;

import java.time.Instant;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabCommitPage;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabCommitDiffPage;

public interface GitLabCommitHistoryPort {
    GitLabCommitPage listCommits(String group, String project, String headSha, String path,
                                Instant since, Instant until, int page);
    GitLabCommitDiffPage readDiff(String group, String project, String commitSha, int page);
}
