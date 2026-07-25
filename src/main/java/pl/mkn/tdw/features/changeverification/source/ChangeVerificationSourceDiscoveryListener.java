package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.util.List;

public interface ChangeVerificationSourceDiscoveryListener {

    ChangeVerificationSourceDiscoveryListener NO_OP = new ChangeVerificationSourceDiscoveryListener() {
    };

    default void onJiraMaterialStarted(String issueKey) {
    }

    default void onJiraMaterialCompleted(String issueKey, JiraIssueMaterial jiraIssue, List<String> limitations) {
    }

    default void onMergeRequestDiscoveryStarted(String issueKey) {
    }

    default void onMergeRequestDiscoveryCompleted(
            String issueKey,
            GitLabMergeRequestSearchResult mergeRequests,
            List<String> limitations
    ) {
    }

    default void onInstructionContextStarted(GitLabMergeRequestSearchResult mergeRequests) {
    }

    default void onInstructionContextCompleted(
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext,
            List<String> limitations
    ) {
    }
}
