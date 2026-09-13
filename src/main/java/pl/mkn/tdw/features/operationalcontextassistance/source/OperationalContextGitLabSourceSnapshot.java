package pl.mkn.tdw.features.operationalcontextassistance.source;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeSlice;

import java.util.List;

public record OperationalContextGitLabSourceSnapshot(
        String project,
        RepositoryGit repositoryGit,
        String requestedRef,
        String commitId,
        List<OperationalContextGitLabSourceFile> files,
        GitLabRepositoryTreeSlice tree,
        List<String> visibilityLimits
) {
    public OperationalContextGitLabSourceSnapshot {
        files = List.copyOf(files);
        tree = tree != null ? tree : new GitLabRepositoryTreeSlice("", 4, List.of(), List.of(), false);
        visibilityLimits = List.copyOf(visibilityLimits);
    }

    public OperationalContextGitLabSourceSnapshot(
            String project, RepositoryGit repositoryGit, String requestedRef, String commitId,
            List<OperationalContextGitLabSourceFile> files, List<String> visibilityLimits
    ) {
        this(project, repositoryGit, requestedRef, commitId, files,
                new GitLabRepositoryTreeSlice("", 4, List.of(), List.of(), false), visibilityLimits);
    }

    public record RepositoryGit(
            String provider,
            String group,
            String project,
            String projectPath,
            String url
    ) {
    }
}
