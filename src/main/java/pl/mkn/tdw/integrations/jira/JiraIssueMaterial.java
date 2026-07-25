package pl.mkn.tdw.integrations.jira;

import java.util.List;

public record JiraIssueMaterial(
        String issueKey,
        String issueUrl,
        String summary,
        String description,
        String issueType,
        String status,
        List<String> labels,
        List<String> acceptanceCriteria,
        List<JiraIssueLink> links,
        List<JiraIssueComment> comments,
        List<String> limitations
) {

    public JiraIssueMaterial {
        labels = labels != null ? List.copyOf(labels) : List.of();
        acceptanceCriteria = acceptanceCriteria != null ? List.copyOf(acceptanceCriteria) : List.of();
        links = links != null ? List.copyOf(links) : List.of();
        comments = comments != null ? List.copyOf(comments) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
