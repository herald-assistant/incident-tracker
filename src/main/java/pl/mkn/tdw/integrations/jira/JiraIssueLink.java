package pl.mkn.tdw.integrations.jira;

public record JiraIssueLink(
        String type,
        String title,
        String url
) {
}
