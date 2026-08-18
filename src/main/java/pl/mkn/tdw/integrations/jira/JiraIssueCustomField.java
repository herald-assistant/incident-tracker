package pl.mkn.tdw.integrations.jira;

public record JiraIssueCustomField(
        String fieldId,
        String id,
        String name,
        String value
) {
}
