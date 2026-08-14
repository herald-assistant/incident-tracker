package pl.mkn.tdw.integrations.jira;

import java.util.List;

public record JiraIssueStatusHistory(
        String issueKey,
        boolean truncated,
        List<JiraIssueStatusTransition> transitions,
        List<String> limitations
) {

    public JiraIssueStatusHistory {
        transitions = transitions != null ? List.copyOf(transitions) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
