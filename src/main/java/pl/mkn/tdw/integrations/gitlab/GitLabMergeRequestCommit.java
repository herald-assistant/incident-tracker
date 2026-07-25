package pl.mkn.tdw.integrations.gitlab;

public record GitLabMergeRequestCommit(
        String id,
        String shortId,
        String title,
        String authorName,
        String createdAt
) {
}
