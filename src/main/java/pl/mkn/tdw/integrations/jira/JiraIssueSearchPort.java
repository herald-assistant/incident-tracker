package pl.mkn.tdw.integrations.jira;

public interface JiraIssueSearchPort {

    JiraIssueSearchResult searchIssues(JiraIssueSearchRequest request);
}
