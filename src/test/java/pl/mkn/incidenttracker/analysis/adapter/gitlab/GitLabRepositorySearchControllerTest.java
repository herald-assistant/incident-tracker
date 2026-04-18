package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.api.ApiExceptionHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitLabRepositorySearchController.class)
class GitLabRepositorySearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitLabRepositorySearchService gitLabRepositorySearchService;

    @Test
    void shouldSearchGitLabRepositoryForValidRequest() throws Exception {
        when(gitLabRepositorySearchService.search(any(GitLabRepositorySearchRequest.class)))
                .thenReturn(new GitLabRepositorySearchResponse(
                        "TENANT-ALPHA",
                        "release-candidate",
                        List.of("document-workflow"),
                        List.of(new GitLabRepositoryProjectCandidate(
                                "TENANT-ALPHA",
                                "WORKFLOWS/DOCUMENT_WORKFLOW",
                                "Matched agreement process project.",
                                120
                        )),
                        List.of(new GitLabRepositoryFileCandidate(
                                "TENANT-ALPHA",
                                "WORKFLOWS/DOCUMENT_WORKFLOW",
                                "release-candidate",
                                "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java",
                                "Matched document keyword.",
                                95
                        )),
                        "OK"
                ));

        mockMvc.perform(post("/api/gitlab/repository/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "agreement-123",
                                  "branch": "release-candidate",
                                  "projectHints": ["document-workflow"],
                                  "keywords": ["document"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("TENANT-ALPHA"))
                .andExpect(jsonPath("$.projectCandidates[0].projectPath").value("WORKFLOWS/DOCUMENT_WORKFLOW"))
                .andExpect(jsonPath("$.fileCandidates[0].projectName").value("WORKFLOWS/DOCUMENT_WORKFLOW"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(gitLabRepositorySearchService).search(new GitLabRepositorySearchRequest(
                "agreement-123",
                "release-candidate",
                List.of("document-workflow"),
                null,
                List.of("document")
        ));
    }

    @Test
    void shouldReturnNotFoundWhenGitLabSearchFindsNoCandidates() throws Exception {
        when(gitLabRepositorySearchService.search(any(GitLabRepositorySearchRequest.class)))
                .thenThrow(new GitLabRepositorySearchException(
                        HttpStatus.NOT_FOUND,
                        new GitLabRepositorySearchResponse(
                                "TENANT-ALPHA",
                                "HEAD",
                                List.of("document-workflow"),
                                List.of(),
                                List.of(),
                                "No GitLab project candidates found for hints: [document-workflow]"
                        )
                ));

        mockMvc.perform(post("/api/gitlab/repository/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectHints": ["document-workflow"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.group").value("TENANT-ALPHA"))
                .andExpect(jsonPath("$.message").value("No GitLab project candidates found for hints: [document-workflow]"));
    }

    @Test
    void shouldReturnBadRequestForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectHints": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabRepositorySearchService);
    }

}

