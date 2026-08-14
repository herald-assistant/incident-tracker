package pl.mkn.tdw.integrations.jira;

import java.time.LocalDate;
import java.util.Locale;

public record JiraIssueSearchRequest(
        String projectKey,
        LocalDate fromDate,
        LocalDate toDateExclusive,
        int pageSize,
        int maxIssues
) {

    public JiraIssueSearchRequest {
        if (projectKey == null || !projectKey.trim().matches("[A-Za-z][A-Za-z0-9_-]{0,49}")) {
            throw new IllegalArgumentException("projectKey has invalid format");
        }
        if (fromDate == null || toDateExclusive == null || !fromDate.isBefore(toDateExclusive)) {
            throw new IllegalArgumentException("fromDate must be before toDateExclusive");
        }
        projectKey = projectKey.trim().toUpperCase(Locale.ROOT);
        pageSize = Math.max(1, Math.min(100, pageSize));
        maxIssues = Math.max(1, maxIssues);
    }
}
