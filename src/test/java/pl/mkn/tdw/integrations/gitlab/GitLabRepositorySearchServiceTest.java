package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GitLabRepositorySearchServiceTest {

    @Test
    void shouldReturnProjectCandidatesWithoutFileSearchWhenNoSearchTermsProvided() {
        var properties = gitLabProperties("CRM");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("CRM", List.of("crm-customer-workflow")))
                .thenReturn(List.of(new GitLabRepositoryProjectCandidate(
                        "CRM",
                        "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                        "Matched customer process project.",
                        120
                )));

        var response = service.search(new GitLabRepositorySearchRequest(
                null,
                "release-candidate",
                List.of("crm-customer-workflow"),
                List.of(),
                List.of()
        ));

        assertEquals("CRM", response.group());
        assertEquals("release-candidate", response.branch());
        assertEquals(1, response.projectCandidates().size());
        assertTrue(response.fileCandidates().isEmpty());
        assertTrue(response.message().contains("File search skipped"));

        verify(repositoryPort).searchProjects("CRM", List.of("crm-customer-workflow"));
    }

    @Test
    void shouldReturnProjectAndFileCandidatesWhenSearchTermsProvided() {
        var properties = gitLabProperties("CRM");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("CRM", List.of("crm-customer-workflow")))
                .thenReturn(List.of(new GitLabRepositoryProjectCandidate(
                        "CRM",
                        "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                        "Matched customer process project.",
                        120
                )));
        when(repositoryPort.searchCandidateFiles(argThat(query ->
                "CRM".equals(query.group())
                        && "release-candidate".equals(query.branch())
                        && query.projectNames().equals(List.of("crm-customer-workflow"))
                        && query.keywords().equals(List.of("customer-profile"))
        ))).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflow/CustomerProfileArchiveService.java",
                "Matched customer profile keyword.",
                95
        )));

        var response = service.search(new GitLabRepositorySearchRequest(
                "customer-123",
                "release-candidate",
                List.of("crm-customer-workflow"),
                List.of(),
                List.of("customer-profile")
        ));

        assertEquals("OK", response.message());
        assertEquals(1, response.projectCandidates().size());
        assertEquals(1, response.fileCandidates().size());
        assertEquals("CRM_WORKFLOWS/CUSTOMER_WORKFLOW", response.fileCandidates().get(0).projectName());
    }

    @Test
    void shouldReturnNotFoundWhenNoProjectCandidatesAreFound() {
        var properties = gitLabProperties("CRM");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("CRM", List.of("crm-customer-workflow")))
                .thenReturn(List.of());

        var exception = assertThrows(
                GitLabRepositorySearchException.class,
                () -> service.search(new GitLabRepositorySearchRequest(
                        null,
                        null,
                        List.of("crm-customer-workflow"),
                        List.of(),
                        List.of("customer-profile")
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getResponse().message().contains("No GitLab project candidates found"));
        verify(repositoryPort).searchProjects("CRM", List.of("crm-customer-workflow"));
        verifyNoMoreInteractions(repositoryPort);
    }

    private static GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setGroup(group);
        properties.setBaseUrl("https://gitlab.example.com");
        return properties;
    }

}

