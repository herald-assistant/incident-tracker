package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionSource;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationPromptPreparationServiceTest {

    private final ChangeVerificationPromptPreparationService service = new ChangeVerificationPromptPreparationService();

    @Test
    void shouldExposeFullBeforeAfterShasAndFocusedDiffToolWithoutInliningPatch() {
        var source = sourceDiscovery();
        var diff = new pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot(
                "CRM/runtime/customer-api", 2, 11L, "a".repeat(40), "b".repeat(40), "c".repeat(40),
                List.of(new pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff(
                        "Old.java", "New.java", false, true, false, "-secretOld\n+secretNew", false, false)), List.of());
        var prepared = service.prepare(new ChangeVerificationJobStartRequest("CRM-123", null, true, false, null, null, null),
                new ChangeVerificationSourceDiscoveryResult(source.issueKey(), source.issueUrl(), source.jiraIssue(),
                        source.mergeRequests(), source.instructionContext(), source.repositories(), List.of(diff), source.limitations()));
        assertThat(prepared.prompt()).contains("a".repeat(40), "b".repeat(40), "c".repeat(40),
                "gitlab_read_merge_request_diff", "Old.java -> New.java", "versionId: 11", "startSha (target tip, context only)");
        assertThat(prepared.prompt()).doesNotContain("secretOld", "secretNew");
        assertThat(prepared.artifactContents().get("change-verification/repository-scope.md"))
                .contains("Old.java -> New.java | MR !2 [renamed]")
                .doesNotContain("src/main/java/CustomerController.java | MRs:");
        assertThat(prepared.artifactContents().get("change-verification/merge-requests.md"))
                .doesNotContain("Old.java", "New.java")
                .contains("availableDiffFileCount: 1");
    }

    @Test
    void shouldRenderPromptWithJiraMergeRequestInstructionsAndContract() {
        var preparation = service.prepare(
                new ChangeVerificationJobStartRequest(
                        "CRM-123",
                        null,
                        true,
                        true,
                        "Focus cleanup.",
                        "gpt-5.4",
                        "medium"
                ),
                sourceDiscovery()
        );

        assertThat(preparation.prompt()).contains(
                "change-verification-orchestrator",
                "change-verification/response-contract.md",
                "interpretationType",
                "additionalChecks",
                "affectedRuleIds",
                "Focus cleanup.",
                "Customer profile status",
                "target issue",
                "feature/CRM-123-status",
                "CRM/runtime/customer-api",
                "src/main/java/CustomerController.java",
                "AGENTS.md",
                "Response Contract"
        );
        assertThat(preparation.prompt()).doesNotContain(
                "change-verification-write-report",
                "\"verificationChecks\"",
                "\"findings\"",
                "\"suggestedActions\"",
                "\"status\": \"PASSED"
        );
        assertThat(preparation.artifactContents().keySet()).containsExactly(
                "change-verification/source-discovery.md",
                "change-verification/jira-issue.md",
                "change-verification/repository-scope.md",
                "change-verification/merge-requests.md",
                "change-verification/instruction-context.md",
                "change-verification/response-contract.md"
        );
        assertThat(preparation.artifactContents().get("change-verification/repository-scope.md"))
                .contains("Repository Scope", "sourceRef: feature/CRM-123-status", "instructionSources:");
        assertThat(preparation.artifactContents().get("change-verification/merge-requests.md"))
                .doesNotContain("changedFiles:", "src/main/java/CustomerController.java");
    }

    private ChangeVerificationSourceDiscoveryResult sourceDiscovery() {
        return new ChangeVerificationSourceDiscoveryResult(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                new JiraIssueMaterial(
                        "CRM-123",
                        "https://jira.example.com/browse/CRM-123",
                        "Customer profile status",
                        "Expose status.",
                        "Story",
                        "Ready",
                        List.of(),
                        List.of("Status is returned."),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new GitLabMergeRequestSearchResult(
                        "CRM-123",
                        "CRM/runtime",
                        List.of(new GitLabMergeRequest(
                                1L,
                                2L,
                                3L,
                                "CRM/runtime/customer-api",
                                "CRM-123 status",
                                "opened",
                                "https://gitlab.example.com/mr/2",
                                "feature/CRM-123-status",
                                "main",
                                "gitlab-author-501",
                                null,
                                null,
                                null,
                                "2",
                                List.of(),
                                List.of(new GitLabMergeRequestChangedFile(
                                        "src/main/java/CustomerController.java",
                                        "src/main/java/CustomerController.java",
                                        false,
                                        false,
                                        false
                                )),
                                List.of()
                        )),
                        List.of()
                ),
                new InstructionContextResult(
                        List.of(new InstructionSource(
                                "CRM/runtime/customer-api",
                                "feature/CRM-123-status",
                                "AGENTS.md",
                                "AGENTS",
                                "Keep controllers thin.",
                                false,
                                null,
                                List.of("src/main/java/CustomerController.java")
                        )),
                        List.of()
                ),
                List.of()
        );
    }
}
