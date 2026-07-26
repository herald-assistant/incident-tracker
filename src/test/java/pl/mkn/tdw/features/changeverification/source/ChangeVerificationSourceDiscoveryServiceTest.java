package pl.mkn.tdw.features.changeverification.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionDiscoveryProperties;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

        var result = service.discover(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
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
    void shouldSearchMergeRequestsForTargetSubTaskParentAndSiblingSubTasks() {
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

        var result = service.discover(new ChangeVerificationJobStartRequest(
                "CRM-124",
                null,
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
                true,
                false,
                null,
                null,
                null
        ));

        assertThat(result.mergeRequests().issueKey()).isEqualTo("CRM-124,CRM-123,CRM-125");
        assertThat(result.mergeRequests().mergeRequests()).extracting(GitLabMergeRequest::title)
                .containsExactly("CRM-124 implementation", "CRM-123 implementation", "CRM-125 implementation");
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-124", 10);
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-123", 10);
        verify(gitLabRepositoryPort).findMergeRequestsByIssueKey("CRM/runtime", "CRM-125", 10);
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
        return new GitLabMergeRequest(
                id,
                id,
                100L + id,
                "CRM/runtime/customer-api",
                issueKey + " implementation",
                "opened",
                "https://gitlab.example.com/mr/" + id,
                "feature/" + issueKey,
                "main",
                "Anna",
                "2026-07-26T00:00:00Z",
                "2026-07-26T00:00:00Z",
                null,
                "1",
                List.of(),
                List.of(),
                List.of()
        );
    }
}
