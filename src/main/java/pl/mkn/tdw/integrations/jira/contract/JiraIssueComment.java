package pl.mkn.tdw.integrations.jira.contract;

public record JiraIssueComment(
        String author,
        String createdAt,
        String body
) {
}
