package pl.mkn.tdw.integrations.jira;

import pl.mkn.tdw.integrations.jira.contract.JiraIssueSearchRequest;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueSearchResult;

public interface JiraIssueSearchPort {

    JiraIssueSearchResult searchIssues(JiraIssueSearchRequest request);
}
