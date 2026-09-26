package pl.mkn.tdw.integrations.jira;

import pl.mkn.tdw.integrations.jira.contract.JiraIssueStatusHistory;

public interface JiraIssueStatusHistoryPort {

    JiraIssueStatusHistory getStatusHistory(String issueKey);
}
