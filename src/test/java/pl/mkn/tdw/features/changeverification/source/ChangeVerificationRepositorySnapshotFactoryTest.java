package pl.mkn.tdw.features.changeverification.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationRepositorySnapshotFactoryTest {

    @Test
    void shouldGroupMergeRequestsByRepositoryAndRefWithChangedFilesAndInstructions() {
        var result = ChangeVerificationRepositorySnapshotFactory.from(
                new GitLabMergeRequestSearchResult(
                        "CRM-123",
                        "CRM/runtime",
                        List.of(
                                mergeRequest(1L, "CRM/runtime/customer-api", "feature/CRM-123", "CustomerController.java"),
                                mergeRequest(2L, "CRM/runtime/customer-api", "feature/CRM-123", "CustomerController.java"),
                                mergeRequest(3L, "CRM/runtime/customer-fe", "feature/CRM-123-ui", "src/app/customer.ts")
                        ),
                        List.of()
                ),
                new InstructionContextResult(
                        List.of(
                                instruction("CRM/runtime/customer-api", "feature/CRM-123", "AGENTS.md"),
                                instruction("CRM/runtime/customer-fe", "feature/CRM-123-ui", ".github/copilot-instructions.md")
                        ),
                        List.of()
                ),
                "CRM/runtime"
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).projectPath()).isEqualTo("CRM/runtime/customer-api");
        assertThat(result.get(0).projectName()).isEqualTo("customer-api");
        assertThat(result.get(0).sourceRef()).isEqualTo("feature/CRM-123");
        assertThat(result.get(0).mergeRequests()).hasSize(2);
        assertThat(result.get(0).changedFiles()).singleElement()
                .satisfies(file -> {
                    assertThat(file.path()).isEqualTo("CustomerController.java");
                    assertThat(file.mergeRequestRefs()).containsExactly("!1", "!2");
                });
        assertThat(result.get(0).instructionSources()).singleElement()
                .extracting(InstructionSource::path)
                .isEqualTo("AGENTS.md");

        assertThat(result.get(1).projectPath()).isEqualTo("CRM/runtime/customer-fe");
        assertThat(result.get(1).changedFiles()).singleElement()
                .extracting(ChangeVerificationChangedFileSnapshot::path)
                .isEqualTo("src/app/customer.ts");
    }

    @Test
    void shouldUseRelativeProjectPathAsToolProjectNameForNestedGroups() {
        var result = ChangeVerificationRepositorySnapshotFactory.from(
                new GitLabMergeRequestSearchResult(
                        "CRM-123",
                        "CRM",
                        List.of(mergeRequest(
                                1L,
                                "CRM/WORKFLOWS/CRM_CASE_WORKFLOW",
                                "feature/CRM-123",
                                "src/main/java/pl/mkn/CaseWorkflow.java"
                        )),
                        List.of()
                ),
                null,
                "CRM"
        );

        assertThat(result).singleElement()
                .satisfies(repository -> {
                    assertThat(repository.projectPath()).isEqualTo("CRM/WORKFLOWS/CRM_CASE_WORKFLOW");
                    assertThat(repository.projectName()).isEqualTo("WORKFLOWS/CRM_CASE_WORKFLOW");
                    assertThat(repository.repositoryName()).isEqualTo("CRM_CASE_WORKFLOW");
                });
    }

    private static GitLabMergeRequest mergeRequest(
            Long iid,
            String projectPath,
            String sourceBranch,
            String changedFile
    ) {
        return new GitLabMergeRequest(
                iid,
                iid,
                100L + iid,
                projectPath,
                "CRM-123 change " + iid,
                "opened",
                "https://gitlab.example.com/" + projectPath + "/-/merge_requests/" + iid,
                sourceBranch,
                "main",
                "gitlab-author-501",
                null,
                null,
                null,
                "1",
                List.of(),
                List.of(new GitLabMergeRequestChangedFile(changedFile, changedFile, false, false, false)),
                List.of()
        );
    }

    private static InstructionSource instruction(String repositoryKey, String ref, String path) {
        return new InstructionSource(
                repositoryKey,
                ref,
                path,
                "AGENTS",
                "Rules",
                false,
                null,
                List.of()
        );
    }
}
