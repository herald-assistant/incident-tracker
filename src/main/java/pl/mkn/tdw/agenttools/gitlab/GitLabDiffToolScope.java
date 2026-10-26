package pl.mkn.tdw.agenttools.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;

import java.util.List;

/** Prepared immutable MR versions and the only revisions authorized for this session. */
public record GitLabDiffToolScope(String group, List<GitLabMergeRequestDiffSnapshot> snapshots) {
    public GitLabDiffToolScope {
        snapshots = List.copyOf(snapshots);
        if (snapshots.stream().anyMatch(s -> !s.projectPath().startsWith(group + "/"))) {
            throw new IllegalArgumentException("MR snapshot is outside the configured group.");
        }
    }

    public GitLabMergeRequestDiffSnapshot requireMergeRequest(String projectName, long iid) {
        return snapshots.stream().filter(s -> s.projectPath().equals(group + "/" + projectName)
                        && s.mergeRequestIid() == iid).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MR is outside the prepared session scope."));
    }

    public void requireRevision(String projectName, String sha) {
        if (projectName == null || sha == null || snapshots.stream().noneMatch(s -> s.hasRevisions()
                && s.projectPath().equals(group + "/" + projectName)
                && (sha.equals(s.baseSha()) || sha.equals(s.headSha()) || sha.equals(s.startSha())))) {
            throw new IllegalArgumentException("Project or SHA is outside the prepared session scope.");
        }
    }
}
