package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

public record GitLabMergeRequest(
        Long id,
        Long iid,
        Long projectId,
        String projectPath,
        String title,
        String state,
        String webUrl,
        String sourceBranch,
        String targetBranch,
        String authorName,
        Long authorId,
        String createdAt,
        String updatedAt,
        String mergedAt,
        String changesCount,
        List<GitLabMergeRequestCommit> commits,
        List<GitLabMergeRequestChangedFile> changedFiles,
        List<String> limitations
) {

    public GitLabMergeRequest {
        commits = commits != null ? List.copyOf(commits) : List.of();
        changedFiles = changedFiles != null ? List.copyOf(changedFiles) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public GitLabMergeRequest(
            Long id,
            Long iid,
            Long projectId,
            String projectPath,
            String title,
            String state,
            String webUrl,
            String sourceBranch,
            String targetBranch,
            String authorName,
            String createdAt,
            String updatedAt,
            String mergedAt,
            String changesCount,
            List<GitLabMergeRequestCommit> commits,
            List<GitLabMergeRequestChangedFile> changedFiles,
            List<String> limitations
    ) {
        this(
                id,
                iid,
                projectId,
                projectPath,
                title,
                state,
                webUrl,
                sourceBranch,
                targetBranch,
                authorName,
                null,
                createdAt,
                updatedAt,
                mergedAt,
                changesCount,
                commits,
                changedFiles,
                limitations
        );
    }
}
