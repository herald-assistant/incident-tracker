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
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchService;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileRole;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelationKind;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRepositoryContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
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

    @MockitoBean
    private GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService;

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
                50
        ));
    }

    @Test
    void shouldBuildEndpointUseCaseContextForValidRequest() throws Exception {
        when(gitLabEndpointUseCaseContextService.buildContext(any(), any(), any(GitLabEndpointUseCaseContextRequest.class)))
                .thenReturn(new GitLabEndpointUseCaseContextResult(
                        new GitLabEndpointUseCaseRepositoryContext(
                                "TENANT-ALPHA",
                                "orders-api",
                                "release-candidate"
                        ),
                        new GitLabEndpointUseCaseEndpointContext(
                                "GET /api/orders/{orderId} -> com.example.orders.OrderController#getOrder",
                                List.of("GET"),
                                "/api/orders/{orderId}",
                                "/api/orders/{orderId}",
                                "com.example.orders.OrderController",
                                "getOrder",
                                "src/main/java/com/example/orders/OrderController.java",
                                17,
                                19,
                                List.of("@PathVariable String orderId"),
                                List.of("OrderResponse"),
                                List.of("RestController", "GetMapping"),
                                GitLabEndpointUseCaseConfidence.HIGH,
                                List.of(),
                                List.of()
                        ),
                        List.of(new GitLabEndpointUseCaseFileCandidate(
                                "src/main/java/com/example/orders/OrderController.java",
                                GitLabEndpointUseCaseFileRole.CONTROLLER,
                                1,
                                List.of("getOrder"),
                                "Endpoint handler and local controller flow.",
                                GitLabEndpointUseCaseConfidence.HIGH
                        )),
                        List.of(new GitLabEndpointUseCaseRelation(
                                "GET /api/orders/{orderId}",
                                "com.example.orders.OrderController#getOrder",
                                GitLabEndpointUseCaseRelationKind.ENDPOINT_HANDLER,
                                GitLabEndpointUseCaseConfidence.HIGH,
                                "Endpoint inventory resolved this handler method."
                        )),
                        List.of(),
                        List.of(),
                        List.of("orders-api:src/main/java/com/example/orders/OrderController.java via gitlab_read_repository_file_outline"),
                        GitLabEndpointUseCaseLimits.defaults(),
                        GitLabEndpointUseCaseConfidence.HIGH
                ));

        mockMvc.perform(post("/api/gitlab/repository/endpoint-use-case-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "TENANT-ALPHA",
                                  "projectName": "orders-api",
                                  "branch": "release-candidate",
                                  "endpointId": "GET /api/orders/{orderId} -> com.example.orders.OrderController#getOrder",
                                  "maxDepth": 4,
                                  "maxFiles": 12,
                                  "reason": "Manualny test kontekstu endpointu."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.group").value("TENANT-ALPHA"))
                .andExpect(jsonPath("$.repository.projectName").value("orders-api"))
                .andExpect(jsonPath("$.endpoint.path").value("/api/orders/{orderId}"))
                .andExpect(jsonPath("$.files[0].role").value("CONTROLLER"))
                .andExpect(jsonPath("$.relations[0].kind").value("ENDPOINT_HANDLER"))
                .andExpect(jsonPath("$.confidence").value("HIGH"));

        verify(gitLabEndpointUseCaseContextService).buildContext(
                eq("TENANT-ALPHA"),
                eq("release-candidate"),
                argThat(request -> "orders-api".equals(request.projectName())
                        && "GET /api/orders/{orderId} -> com.example.orders.OrderController#getOrder".equals(request.endpointId())
                        && request.httpMethod() == null
                        && request.endpointPath() == null
                        && request.maxDepth() == 4
                        && request.maxFiles() == 12
                        && "Manualny test kontekstu endpointu.".equals(request.reason()))
        );
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

    @Test
    void shouldReturnBadRequestForInvalidEndpointUseCaseContextRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/endpoint-use-case-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "",
                                  "projectName": "",
                                  "branch": "",
                                  "maxDepth": 99,
                                  "maxFiles": 99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabEndpointUseCaseContextService);
    }

}
