package pl.mkn.tdw.integrations.jira;

import org.springframework.util.StringUtils;

public record JiraIssueMaterialRequest(
        String issueKey,
        boolean includeComments,
        boolean includeRemoteLinks,
        boolean includeIssueLinks,
        boolean includeSubTasks,
        boolean includeParent,
        boolean includeConfluencePages
) {

    public JiraIssueMaterialRequest {
        if (!StringUtils.hasText(issueKey)) {
            throw new IllegalArgumentException("issueKey must not be blank");
        }
        issueKey = issueKey.trim();
        includeConfluencePages = includeRemoteLinks && includeConfluencePages;
    }

    public static JiraIssueMaterialRequest detailed(String issueKey) {
        return new JiraIssueMaterialRequest(issueKey, true, true, true, true, true, true);
    }

    public static JiraIssueMaterialRequest assessment(String issueKey) {
        return new JiraIssueMaterialRequest(issueKey, false, true, true, false, false, true);
    }
}
