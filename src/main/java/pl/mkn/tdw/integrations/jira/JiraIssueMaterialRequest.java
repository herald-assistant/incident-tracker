package pl.mkn.tdw.integrations.jira;

import org.springframework.util.StringUtils;

import java.util.List;

public record JiraIssueMaterialRequest(
        String issueKey,
        boolean includeComments,
        boolean includeRemoteLinks,
        boolean includeIssueLinks,
        boolean includeSubTasks,
        boolean includeParent,
        boolean includeConfluencePages,
        List<String> customFieldIds
) {

    public JiraIssueMaterialRequest {
        if (!StringUtils.hasText(issueKey)) {
            throw new IllegalArgumentException("issueKey must not be blank");
        }
        issueKey = issueKey.trim();
        includeConfluencePages = includeRemoteLinks && includeConfluencePages;
        customFieldIds = customFieldIds != null
                ? customFieldIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList()
                : List.of();
    }

    public JiraIssueMaterialRequest(
            String issueKey,
            boolean includeComments,
            boolean includeRemoteLinks,
            boolean includeIssueLinks,
            boolean includeSubTasks,
            boolean includeParent,
            boolean includeConfluencePages
    ) {
        this(
                issueKey,
                includeComments,
                includeRemoteLinks,
                includeIssueLinks,
                includeSubTasks,
                includeParent,
                includeConfluencePages,
                List.of()
        );
    }

    public static JiraIssueMaterialRequest detailed(String issueKey) {
        return new JiraIssueMaterialRequest(issueKey, true, true, true, true, true, true);
    }

    public static JiraIssueMaterialRequest assessment(String issueKey) {
        return new JiraIssueMaterialRequest(issueKey, false, true, true, false, false, true);
    }

    public static JiraIssueMaterialRequest assessment(String issueKey, List<String> customFieldIds) {
        return new JiraIssueMaterialRequest(issueKey, false, true, true, false, false, true, customFieldIds);
    }
}
