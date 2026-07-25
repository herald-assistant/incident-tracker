package pl.mkn.tdw.integrations.jira;

public interface JiraIssuePort {

    JiraIssueMaterial getIssueMaterial(String issueKey);
}
