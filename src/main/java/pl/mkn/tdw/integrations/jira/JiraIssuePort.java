package pl.mkn.tdw.integrations.jira;

public interface JiraIssuePort {

    JiraIssueMaterial getIssueMaterial(String issueKey);

    default JiraIssueMaterial getIssueMaterial(JiraIssueMaterialRequest request) {
        return getIssueMaterial(request.issueKey());
    }
}
