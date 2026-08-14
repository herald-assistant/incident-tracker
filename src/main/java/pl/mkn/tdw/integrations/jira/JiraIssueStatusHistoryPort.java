package pl.mkn.tdw.integrations.jira;

public interface JiraIssueStatusHistoryPort {

    JiraIssueStatusHistory getStatusHistory(String issueKey);
}
