package pl.mkn.incidenttracker.api.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointUseCaseInput;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchService;

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

    @MockitoBean
    private GitLabRepositoryEndpointService gitLabRepositoryEndpointService;

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
    void shouldListGitLabRepositoryEndpointsForValidRequest() throws Exception {
        when(gitLabRepositoryEndpointService.listEndpoints(any(GitLabRepositoryEndpointListRequest.class)))
                .thenReturn(new GitLabRepositoryEndpointListResult(
                        "TENANT-ALPHA",
                        "orders-api",
                        "release-candidate",
                        "/api/orders",
                        "GET",
                        "src/main/java",
                        2,
                        2,
                        false,
                        List.of(new GitLabRepositoryEndpoint(
                                "GET /api/orders/{orderId} -> com.example.orders.OrderController#getOrder",
                                List.of("GET"),
                                "/api/orders/{orderId}",
                                null,
                                "com.example.orders.OrderController",
                                "getOrder",
                                "src/main/java/com/example/orders/OrderController.java",
                                17,
                                19,
                                List.of("@PathVariable String orderId"),
                                List.of("ResponseEntity<OrderResponse>"),
                                List.of("RestController", "GetMapping"),
                                "high",
                                List.of(),
                                new GitLabRepositoryEndpointUseCaseInput(
                                        "orders-api",
                                        "GET /api/orders/{orderId} -> com.example.orders.OrderController#getOrder",
                                        List.of("GET"),
                                        "/api/orders/{orderId}",
                                        "src/main/java/com/example/orders/OrderController.java",
                                        17,
                                        19
                                ),
                                List.of("orders-api:src/main/java/com/example/orders/OrderController.java lines 17-19 via gitlab_read_repository_file_chunk")
                        )),
                        List.of()
                ));

        mockMvc.perform(post("/api/gitlab/repository/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "TENANT-ALPHA",
                                  "projectName": "orders-api",
                                  "branch": "release-candidate",
                                  "endpointPathPrefix": "/api/orders",
                                  "httpMethod": "GET",
                                  "sourcePathPrefix": "src/main/java",
                                  "maxScannedFiles": 50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("TENANT-ALPHA"))
                .andExpect(jsonPath("$.projectName").value("orders-api"))
                .andExpect(jsonPath("$.candidateFileCount").value(2))
                .andExpect(jsonPath("$.endpoints[0].path").value("/api/orders/{orderId}"))
                .andExpect(jsonPath("$.endpoints[0].controllerClass").value("com.example.orders.OrderController"))
                .andExpect(jsonPath("$.endpoints[0].handlerMethod").value("getOrder"));

        verify(gitLabRepositoryEndpointService).listEndpoints(new GitLabRepositoryEndpointListRequest(
                "TENANT-ALPHA",
                "orders-api",
                "release-candidate",
                "/api/orders",
                "GET",
                "src/main/java",
                50
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

    @Test
    void shouldReturnBadRequestForInvalidEndpointListingRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "",
                                  "projectName": "",
                                  "branch": "",
                                  "maxScannedFiles": 999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabRepositoryEndpointService);
    }
}
