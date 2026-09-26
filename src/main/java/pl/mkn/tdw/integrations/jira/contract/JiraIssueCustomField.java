package pl.mkn.tdw.integrations.jira.contract;

public record JiraIssueCustomField(
        String fieldId,
        String id,
        String name,
        String value
) {
}
