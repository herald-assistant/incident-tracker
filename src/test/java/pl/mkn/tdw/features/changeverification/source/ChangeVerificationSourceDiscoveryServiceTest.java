package pl.mkn.tdw.features.changeverification.source;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionDiscoveryProperties;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeVerificationSourceDiscoveryServiceTest {

    @Test
    void shouldSearchMergeRequestsForTargetIssueAndSubTasks() {
        var jiraIssuePort = mock(JiraIssuePort.class);
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        gitLabProperties.setMaxMergeRequests(10);
        var service = new ChangeVerificationSourceDiscoveryService(
                jiraIssuePort,
                gitLabRepositoryPort,
                gitLabProperties,
                new InstructionContextDiscoveryService(gitLabRepositoryPort, new InstructionDiscoveryProperties()),
                new ChangeVerificationOperationalContextMatcher(ignored -> OperationalContextCatalog.empty())
        );

        when(jiraIssuePort.getIssueMaterial("CRM-123")).thenReturn(issueWithSubTask());
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-123", "CRM/runtime", List.of(mergeRequest(1L, "CRM-123")), List.of()));
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-124", "CRM/runtime", List.of(mergeRequest(2L, "CRM-124")), List.of()));
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-123")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-124")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "main")).thenReturn(true);

        var result = service.discover(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                true,
                false,
                null,
                null,
                null
        ));

        assertThat(result.mergeRequests().issueKey()).isEqualTo("CRM-123,CRM-124");
        assertThat(result.mergeRequests().mergeRequests()).extracting(GitLabMergeRequest::title)
                .containsExactly("CRM-123 implementation", "CRM-124 implementation");
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10);
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10);
    }

    @Test
    void shouldNotExpandSubTaskMergeRequestSearchToParentOrSiblings() {
        var jiraIssuePort = mock(JiraIssuePort.class);
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        gitLabProperties.setMaxMergeRequests(10);
        var service = new ChangeVerificationSourceDiscoveryService(
                jiraIssuePort,
                gitLabRepositoryPort,
                gitLabProperties,
                new InstructionContextDiscoveryService(gitLabRepositoryPort, new InstructionDiscoveryProperties()),
                new ChangeVerificationOperationalContextMatcher(ignored -> OperationalContextCatalog.empty())
        );

        when(jiraIssuePort.getIssueMaterial("CRM-124")).thenReturn(subTaskWithParent());
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-124", "CRM/runtime", List.of(mergeRequest(2L, "CRM-124")), List.of()));
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-123", "CRM/runtime", List.of(mergeRequest(1L, "CRM-123")), List.of()));
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-125", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-125", "CRM/runtime", List.of(mergeRequest(3L, "CRM-125")), List.of()));
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-124")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-123")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-125")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "main")).thenReturn(true);

        var result = service.discover(new ChangeVerificationJobStartRequest(
                "CRM-124",
                null,
                true,
                false,
                null,
                null,
                null
        ));

        assertThat(result.mergeRequests().issueKey()).isEqualTo("CRM-124");
        assertThat(result.mergeRequests().mergeRequests()).extracting(GitLabMergeRequest::title)
                .containsExactly("CRM-124 implementation");
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10);
        verify(gitLabRepositoryPort, never()).findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10);
        verify(gitLabRepositoryPort, never()).findMergeRequestsByIssueKey("CRM/runtime", "CRM-125", 10);
    }

    @Test
    void shouldUseTargetBranchForAnalysisWhenSourceBranchWasRemoved() {
        var jiraIssuePort = mock(JiraIssuePort.class);
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        gitLabProperties.setMaxMergeRequests(10);
        var service = new ChangeVerificationSourceDiscoveryService(
                jiraIssuePort,
                gitLabRepositoryPort,
                gitLabProperties,
                new InstructionContextDiscoveryService(gitLabRepositoryPort, new InstructionDiscoveryProperties()),
                new ChangeVerificationOperationalContextMatcher(ignored -> OperationalContextCatalog.empty())
        );

        when(jiraIssuePort.getIssueMaterial("CRM-123")).thenReturn(issueWithSubTask());
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-123", "CRM/runtime", List.of(mergeRequest(1L, "CRM-123")), List.of()));
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-124", "CRM/runtime", List.of(), List.of()));
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-123")).thenReturn(false);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "main")).thenReturn(true);

        var result = service.discover(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                true,
                true,
                null,
                null,
                null
        ));

        assertThat(result.repositories()).singleElement()
                .satisfies(repository -> {
                    assertThat(repository.sourceRef()).isEqualTo("feature/CRM-123");
                    assertThat(repository.sourceRefAvailable()).isFalse();
                    assertThat(repository.targetRef()).isEqualTo("main");
                    assertThat(repository.targetRefAvailable()).isTrue();
                    assertThat(repository.analysisRef()).isEqualTo("main");
                    assertThat(repository.analysisRefSource()).isEqualTo("TARGET_REF");
                    assertThat(repository.limitations()).anySatisfy(limitation ->
                            assertThat(limitation).contains("Source branch 'feature/CRM-123' is not available"));
                });
        verify(gitLabRepositoryPort, atLeastOnce()).readFile(org.mockito.ArgumentMatchers.argThat(request ->
                "CRM/runtime/customer-api".equals(request.repositoryKey()) && "main".equals(request.ref())));
    }

    @Test
    void shouldDiscoverInstructionsOncePerRepositoryAndAnalysisRefWithAllChangedFiles() {
        var jiraIssuePort = mock(JiraIssuePort.class);
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var instructionContextDiscoveryService = mock(InstructionContextDiscoveryService.class);
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        gitLabProperties.setMaxMergeRequests(10);
        var service = new ChangeVerificationSourceDiscoveryService(
                jiraIssuePort,
                gitLabRepositoryPort,
                gitLabProperties,
                instructionContextDiscoveryService,
                new ChangeVerificationOperationalContextMatcher(ignored -> OperationalContextCatalog.empty())
        );

        var firstFile = new GitLabMergeRequestChangedFile(
                "src/customer/CustomerService.java",
                "src/customer/CustomerService.java",
                false,
                false,
                false
        );
        var secondFile = new GitLabMergeRequestChangedFile(
                "src/customer/CustomerController.java",
                "src/customer/CustomerController.java",
                false,
                false,
                false
        );
        when(jiraIssuePort.getIssueMaterial("CRM-123")).thenReturn(issueWithSubTask());
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10))
                .thenReturn(new GitLabMergeRequestSearchResult(
                        "CRM-123",
                        "CRM/runtime",
                        List.of(
                                mergeRequest(1L, "CRM-123", "feature/CRM-123", List.of(firstFile)),
                                mergeRequest(2L, "CRM-123", "feature/CRM-123", List.of(firstFile, secondFile))
                        ),
                        List.of()
                ));
        when(gitLabRepositoryPort.findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-124", "CRM/runtime", List.of(), List.of()));
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "feature/CRM-123")).thenReturn(true);
        when(gitLabRepositoryPort.branchExists("CRM/runtime", "customer-api", "main")).thenReturn(true);
        when(instructionContextDiscoveryService.discover(any())).thenReturn(new InstructionContextResult(List.of(), List.of()));

        service.discover(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                true,
                true,
                null,
                null,
                null
        ));

        var requestCaptor = ArgumentCaptor.forClass(InstructionContextRequest.class);
        verify(instructionContextDiscoveryService).discover(requestCaptor.capture());
        assertThat(requestCaptor.getValue().scopes()).singleElement().satisfies(scope -> {
            assertThat(scope.repositoryKey()).isEqualTo("CRM/runtime/customer-api");
            assertThat(scope.ref()).isEqualTo("feature/CRM-123");
            assertThat(scope.changedFilePaths()).containsExactly(
                    "src/customer/CustomerService.java",
                    "src/customer/CustomerController.java"
            );
        });
    }

    private static JiraIssueMaterial issueWithSubTask() {
        return new JiraIssueMaterial(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "Parent story",
                "Parent description",
                "Story",
                "In Progress",
                List.of(),
                List.of(),
                List.of(),
                List.of(subTask()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JiraIssueMaterial subTask() {
        return new JiraIssueMaterial(
                "CRM-124",
                "https://jira.example.com/browse/CRM-124",
                "Backend subtask",
                "Subtask description",
                "Sub-task",
                "In Progress",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JiraIssueMaterial subTaskWithParent() {
        return new JiraIssueMaterial(
                "CRM-124",
                "https://jira.example.com/browse/CRM-124",
                "Backend subtask",
                "Subtask description",
                "Sub-task",
                "In Progress",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                parentIssueWithSibling(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JiraIssueMaterial parentIssueWithSibling() {
        return new JiraIssueMaterial(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "Parent story",
                "Parent description",
                "Story",
                "In Progress",
                List.of(),
                List.of(),
                List.of(),
                List.of(siblingSubTask()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static JiraIssueMaterial siblingSubTask() {
        return new JiraIssueMaterial(
                "CRM-125",
                "https://jira.example.com/browse/CRM-125",
                "Frontend subtask",
                "Sibling description",
                "Sub-task",
                "In Progress",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static GitLabMergeRequest mergeRequest(Long id, String issueKey) {
        return mergeRequest(id, issueKey, "feature/" + issueKey, List.of());
    }

    private static GitLabMergeRequest mergeRequest(
            Long id,
            String issueKey,
            String sourceBranch,
            List<GitLabMergeRequestChangedFile> changedFiles
    ) {
        return new GitLabMergeRequest(
                id,
                id,
                100L + id,
                "CRM/runtime/customer-api",
                issueKey + " implementation",
                "opened",
                "https://gitlab.example.com/mr/" + id,
                sourceBranch,
                "main",
                "gitlab-author-100",
                "2026-07-26T00:00:00Z",
                "2026-07-26T00:00:00Z",
                null,
                "1",
                List.of(),
                changedFiles,
                List.of()
        );
    }
}
