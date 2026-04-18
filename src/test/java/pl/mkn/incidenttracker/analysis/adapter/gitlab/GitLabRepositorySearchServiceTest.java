package pl.mkn.incidenttracker.analysis.adapter.gitlab;

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
        var properties = gitLabProperties("TENANT-ALPHA");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("TENANT-ALPHA", List.of("document-workflow")))
                .thenReturn(List.of(new GitLabRepositoryProjectCandidate(
                        "TENANT-ALPHA",
                        "WORKFLOWS/DOCUMENT_WORKFLOW",
                        "Matched agreement process project.",
                        120
                )));

        var response = service.search(new GitLabRepositorySearchRequest(
                null,
                "release-candidate",
                List.of("document-workflow"),
                List.of(),
                List.of()
        ));

        assertEquals("TENANT-ALPHA", response.group());
        assertEquals("release-candidate", response.branch());
        assertEquals(1, response.projectCandidates().size());
        assertTrue(response.fileCandidates().isEmpty());
        assertTrue(response.message().contains("File search skipped"));

        verify(repositoryPort).searchProjects("TENANT-ALPHA", List.of("document-workflow"));
    }

    @Test
    void shouldReturnProjectAndFileCandidatesWhenSearchTermsProvided() {
        var properties = gitLabProperties("TENANT-ALPHA");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("TENANT-ALPHA", List.of("document-workflow")))
                .thenReturn(List.of(new GitLabRepositoryProjectCandidate(
                        "TENANT-ALPHA",
                        "WORKFLOWS/DOCUMENT_WORKFLOW",
                        "Matched agreement process project.",
                        120
                )));
        when(repositoryPort.searchCandidateFiles(argThat(query ->
                "TENANT-ALPHA".equals(query.group())
                        && "release-candidate".equals(query.branch())
                        && query.projectNames().equals(List.of("document-workflow"))
                        && query.keywords().equals(List.of("document"))
        ))).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "TENANT-ALPHA",
                "WORKFLOWS/DOCUMENT_WORKFLOW",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java",
                "Matched document keyword.",
                95
        )));

        var response = service.search(new GitLabRepositorySearchRequest(
                "agreement-123",
                "release-candidate",
                List.of("document-workflow"),
                List.of(),
                List.of("document")
        ));

        assertEquals("OK", response.message());
        assertEquals(1, response.projectCandidates().size());
        assertEquals(1, response.fileCandidates().size());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", response.fileCandidates().get(0).projectName());
    }

    @Test
    void shouldReturnNotFoundWhenNoProjectCandidatesAreFound() {
        var properties = gitLabProperties("TENANT-ALPHA");
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabRepositorySearchService(properties, repositoryPort);

        when(repositoryPort.searchProjects("TENANT-ALPHA", List.of("document-workflow")))
                .thenReturn(List.of());

        var exception = assertThrows(
                GitLabRepositorySearchException.class,
                () -> service.search(new GitLabRepositorySearchRequest(
                        null,
                        null,
                        List.of("document-workflow"),
                        List.of(),
                        List.of("document")
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getResponse().message().contains("No GitLab project candidates found"));
        verify(repositoryPort).searchProjects("TENANT-ALPHA", List.of("document-workflow"));
        verifyNoMoreInteractions(repositoryPort);
    }

    private static GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setGroup(group);
        properties.setBaseUrl("https://gitlab.example.com");
        return properties;
    }

}

