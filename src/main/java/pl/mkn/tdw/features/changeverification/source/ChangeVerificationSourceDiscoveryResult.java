package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.util.List;

public record ChangeVerificationSourceDiscoveryResult(
        String issueKey,
        String issueUrl,
        JiraIssueMaterial jiraIssue,
        GitLabMergeRequestSearchResult mergeRequests,
        InstructionContextResult instructionContext,
        List<String> limitations
) {

    public ChangeVerificationSourceDiscoveryResult {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
