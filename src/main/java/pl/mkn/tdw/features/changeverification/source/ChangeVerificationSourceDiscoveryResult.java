package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;

import java.util.List;

public record ChangeVerificationSourceDiscoveryResult(
        String issueKey,
        String issueUrl,
        JiraIssueMaterial jiraIssue,
        GitLabMergeRequestSearchResult mergeRequests,
        InstructionContextResult instructionContext,
        List<ChangeVerificationRepositorySnapshot> repositories,
        List<GitLabMergeRequestDiffSnapshot> mergeRequestDiffs,
        List<String> limitations
) {

    public ChangeVerificationSourceDiscoveryResult {
        mergeRequestDiffs = mergeRequestDiffs == null ? List.of() : List.copyOf(mergeRequestDiffs);
        repositories = repositories != null
                ? List.copyOf(repositories)
                : ChangeVerificationRepositorySnapshotFactory.from(mergeRequests, instructionContext);
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public ChangeVerificationSourceDiscoveryResult(
            String issueKey,
            String issueUrl,
            JiraIssueMaterial jiraIssue,
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext,
            List<String> limitations
    ) {
        this(
                issueKey,
                issueUrl,
                jiraIssue,
                mergeRequests,
                instructionContext,
                ChangeVerificationRepositorySnapshotFactory.from(mergeRequests, instructionContext),
                List.of(),
                limitations
        );
    }
}
