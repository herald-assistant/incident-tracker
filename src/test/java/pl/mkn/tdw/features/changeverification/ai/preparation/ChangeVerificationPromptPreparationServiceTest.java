package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationPromptPreparationServiceTest {

    private final ChangeVerificationPromptPreparationService service = new ChangeVerificationPromptPreparationService();

    @Test
    void shouldRenderPromptWithJiraMergeRequestInstructionsAndContract() {
        var preparation = service.prepare(
                new ChangeVerificationJobStartRequest(
                        "CRM-123",
                        null,
                        List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
                        true,
                        true,
                        "Focus cleanup.",
                        "gpt-5.4",
                        "medium"
                ),
                sourceDiscovery()
        );

        assertThat(preparation.prompt()).contains(
                "change-verification-compliance-check",
                "Focus cleanup.",
                "Customer profile status",
                "feature/CRM-123-status",
                "CRM/runtime/customer-api",
                "src/main/java/CustomerController.java",
                "AGENTS.md",
                "Response Contract"
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
                                "Jan Nowak",
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
