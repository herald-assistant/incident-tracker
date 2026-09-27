package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabMergeRequestCommit(
        String id,
        String shortId,
        String title,
        String authorName,
        String createdAt
) {
}
