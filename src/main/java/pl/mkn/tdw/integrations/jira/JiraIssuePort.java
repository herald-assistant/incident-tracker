package pl.mkn.tdw.integrations.jira;

import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterialRequest;

public interface JiraIssuePort {

    JiraIssueMaterial getIssueMaterial(String issueKey);

    default JiraIssueMaterial getIssueMaterial(JiraIssueMaterialRequest request) {
        return getIssueMaterial(request.issueKey());
    }
}
