package pl.mkn.tdw.integrations.jira.contract;

public record JiraIssueLink(
        String type,
        String title,
        String url
) {
}
