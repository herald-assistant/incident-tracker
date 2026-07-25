package pl.mkn.tdw.integrations.jira;

public record JiraIssueComment(
        String author,
        String createdAt,
        String body
) {
}
