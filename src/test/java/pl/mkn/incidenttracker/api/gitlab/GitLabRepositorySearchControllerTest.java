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
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabJavaMethodSliceService;
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

    @MockitoBean
    private GitLabRepositoryFilesByPathApiService gitLabRepositoryFilesByPathApiService;

    @MockitoBean
    private GitLabJavaMethodSliceService gitLabJavaMethodSliceService;

    @Test
    void shouldSearchGitLabRepositoryForValidRequest() throws Exception {
        when(gitLabRepositorySearchService.search(any(GitLabRepositorySearchRequest.class)))
                .thenReturn(new GitLabRepositorySearchResponse(
                        "CRM",
                        "release-candidate",
                        List.of("crm-customer-workflow"),
                        List.of(new GitLabRepositoryProjectCandidate(
                                "CRM",
                                "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                                "Matched customer process project.",
                                120
                        )),
                        List.of(new GitLabRepositoryFileCandidate(
                                "CRM",
                                "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                                "release-candidate",
                                "src/main/java/com/example/synthetic/workflow/CustomerProfileArchiveService.java",
                                "Matched customer profile keyword.",
                                95
                        )),
                        "OK"
                ));

        mockMvc.perform(post("/api/gitlab/repository/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "customer-123",
                                  "branch": "release-candidate",
                                  "projectHints": ["crm-customer-workflow"],
                                  "keywords": ["customer-profile"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("CRM"))
                .andExpect(jsonPath("$.projectCandidates[0].projectPath").value("CRM_WORKFLOWS/CUSTOMER_WORKFLOW"))
                .andExpect(jsonPath("$.fileCandidates[0].projectName").value("CRM_WORKFLOWS/CUSTOMER_WORKFLOW"))
                .andExpect(jsonPath("$.message").value("OK"));

        verify(gitLabRepositorySearchService).search(new GitLabRepositorySearchRequest(
                "customer-123",
                "release-candidate",
                List.of("crm-customer-workflow"),
                null,
                List.of("customer-profile")
        ));
    }

    @Test
    void shouldListGitLabRepositoryEndpointsForValidRequest() throws Exception {
        when(gitLabRepositoryEndpointService.listEndpoints(any(GitLabRepositoryEndpointListRequest.class)))
                .thenReturn(new GitLabRepositoryEndpointListResult(
                        "CRM",
                        "crm-customer-api",
                        "release-candidate",
                        "/api/customers",
                        "GET",
                        2,
                        2,
                        false,
                        List.of(new GitLabRepositoryEndpoint(
                                "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                                List.of("GET"),
                                "/api/customers/{customerId}",
                                null,
                                "com.example.crm.customer.CustomerController",
                                "getCustomer",
                                "src/main/java/com/example/crm/customer/CustomerController.java",
                                17,
                                19,
                                List.of("@PathVariable String customerId"),
                                List.of("ResponseEntity<OrderResponse>"),
                                List.of("RestController", "GetMapping"),
                                "high",
                                List.of(),
                                List.of("crm-customer-api:src/main/java/com/example/crm/customer/CustomerController.java lines 17-19 via gitlab_read_repository_file_chunk")
                        )),
                        List.of()
                ));

        mockMvc.perform(post("/api/gitlab/repository/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM",
                                  "projectName": "crm-customer-api",
                                  "branch": "release-candidate",
                                  "endpointPathPrefix": "/api/customers",
                                  "httpMethod": "GET",
                                  "maxScannedFiles": 50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("CRM"))
                .andExpect(jsonPath("$.projectName").value("crm-customer-api"))
                .andExpect(jsonPath("$.candidateFileCount").value(2))
                .andExpect(jsonPath("$.endpoints[0].path").value("/api/customers/{customerId}"))
                .andExpect(jsonPath("$.endpoints[0].controllerClass").value("com.example.crm.customer.CustomerController"))
                .andExpect(jsonPath("$.endpoints[0].handlerMethod").value("getCustomer"));

        verify(gitLabRepositoryEndpointService).listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-customer-api",
                "release-candidate",
                "/api/customers",
                "GET",
                50
        ));
    }

    @Test
    void shouldBuildEndpointUseCaseContextForValidRequest() throws Exception {
        when(gitLabEndpointUseCaseContextService.buildContext(any(), any(), any(GitLabEndpointUseCaseContextRequest.class)))
                .thenReturn(new GitLabEndpointUseCaseContextResult(
                        new GitLabEndpointUseCaseRepositoryContext(
                                "CRM",
                                "crm-customer-api",
                                "release-candidate"
                        ),
                        new GitLabEndpointUseCaseEndpointContext(
                                "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                                List.of("GET"),
                                "/api/customers/{customerId}",
                                "/api/customers/{customerId}",
                                "com.example.crm.customer.CustomerController",
                                "getCustomer",
                                "src/main/java/com/example/crm/customer/CustomerController.java",
                                17,
                                19,
                                List.of("@PathVariable String customerId"),
                                List.of("OrderResponse"),
                                List.of("RestController", "GetMapping"),
                                GitLabEndpointUseCaseConfidence.HIGH,
                                List.of(),
                                List.of()
                        ),
                        List.of(new GitLabEndpointUseCaseFileCandidate(
                                "src/main/java/com/example/crm/customer/CustomerController.java",
                                GitLabEndpointUseCaseFileRole.CONTROLLER,
                                1,
                                List.of("getCustomer"),
                                "Endpoint handler and local controller flow.",
                                GitLabEndpointUseCaseConfidence.HIGH
                        )),
                        List.of(new GitLabEndpointUseCaseRelation(
                                "GET /api/customers/{customerId}",
                                "com.example.crm.customer.CustomerController#getCustomer",
                                GitLabEndpointUseCaseRelationKind.ENDPOINT_HANDLER,
                                GitLabEndpointUseCaseConfidence.HIGH,
                                "Endpoint customer-profile resolved this handler method."
                        )),
                        List.of(),
                        List.of(),
                        List.of("crm-customer-api:src/main/java/com/example/crm/customer/CustomerController.java via gitlab_read_repository_file_outline"),
                        GitLabEndpointUseCaseLimits.defaults(),
                        GitLabEndpointUseCaseConfidence.HIGH
                ));

        mockMvc.perform(post("/api/gitlab/repository/endpoint-use-case-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM",
                                  "projectName": "crm-customer-api",
                                  "branch": "release-candidate",
                                  "endpointId": "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                                  "maxDepth": 4,
                                  "maxFiles": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.group").value("CRM"))
                .andExpect(jsonPath("$.repository.projectName").value("crm-customer-api"))
                .andExpect(jsonPath("$.endpoint.path").value("/api/customers/{customerId}"))
                .andExpect(jsonPath("$.files[0].role").value("CONTROLLER"))
                .andExpect(jsonPath("$.relations[0].kind").value("ENDPOINT_HANDLER"))
                .andExpect(jsonPath("$.confidence").value("HIGH"));

        verify(gitLabEndpointUseCaseContextService).buildContext(
                eq("CRM"),
                eq("release-candidate"),
                argThat(request -> "crm-customer-api".equals(request.projectName())
                        && "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer".equals(request.endpointId())
                        && request.httpMethod() == null
                        && request.endpointPath() == null
                        && request.maxDepth() == 4
                        && request.maxFiles() == 12)
        );
    }

    @Test
    void shouldReadRepositoryFilesByPathForValidRequest() throws Exception {
        when(gitLabRepositoryFilesByPathApiService.readFiles(any(GitLabRepositoryFilesByPathApiRequest.class)))
                .thenReturn(new GitLabRepositoryFilesByPathApiResponse(
                        "CRM",
                        "crm-customer-api",
                        "release-candidate",
                        2,
                        2,
                        2,
                        0,
                        54,
                        false,
                        false,
                        List.of(
                                new GitLabRepositoryFileByPathApiResult(
                                        "CRM",
                                        "crm-customer-api",
                                        "release-candidate",
                                        "src/main/java/com/example/crm/customer/CustomerController.java",
                                         "class CustomerController {}",
                                         false,
                                         "entrypoint",
                                         27,
                                         128L,
                                         "sha-controller",
                                         "blob-controller",
                                         "commit-controller",
                                         "last-controller",
                                         "2026-06-14T10:20:00.000Z",
                                         "RESOLVED",
                                         null,
                                         null
                                 ),
                                 new GitLabRepositoryFileByPathApiResult(
                                         "CRM",
                                         "crm-customer-api",
                                        "release-candidate",
                                        "src/main/java/com/example/crm/customer/CustomerService.java",
                                         "class CustomerService {}",
                                         false,
                                         "service-or-orchestrator",
                                         27,
                                         256L,
                                         "sha-service",
                                         "blob-service",
                                         "commit-service",
                                         "last-service",
                                         "2026-06-15T11:30:00.000Z",
                                         "RESOLVED",
                                         null,
                                         null
                                 )
                         )
                ));

        mockMvc.perform(post("/api/gitlab/repository/files/by-path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM",
                                  "projectName": "crm-customer-api",
                                  "branch": "release-candidate",
                                  "filePaths": [
                                    "src/main/java/com/example/crm/customer/CustomerController.java",
                                    "src/main/java/com/example/crm/customer/CustomerService.java"
                                  ],
                                  "maxCharactersPerFile": 4000,
                                  "maxTotalCharacters": 60000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("CRM"))
                .andExpect(jsonPath("$.projectName").value("crm-customer-api"))
                .andExpect(jsonPath("$.returnedFileCount").value(2))
                .andExpect(jsonPath("$.files[0].filePath").value("src/main/java/com/example/crm/customer/CustomerController.java"))
                .andExpect(jsonPath("$.files[0].inferredRole").value("entrypoint"))
                .andExpect(jsonPath("$.files[0].lastCommitId").value("last-controller"))
                .andExpect(jsonPath("$.files[0].lastModifiedAt").value("2026-06-14T10:20:00.000Z"));

        verify(gitLabRepositoryFilesByPathApiService).readFiles(argThat(request ->
                "CRM".equals(request.group())
                        && "crm-customer-api".equals(request.projectName())
                        && "release-candidate".equals(request.branch())
                        && request.filePaths().size() == 2
                        && request.maxCharactersPerFile() == 4000
                        && request.maxTotalCharacters() == 60000
        ));
    }

    @Test
    void shouldReturnNotFoundWhenGitLabSearchFindsNoCandidates() throws Exception {
        when(gitLabRepositorySearchService.search(any(GitLabRepositorySearchRequest.class)))
                .thenThrow(new GitLabRepositorySearchException(
                        HttpStatus.NOT_FOUND,
                        new GitLabRepositorySearchResponse(
                                "CRM",
                                "HEAD",
                                List.of("crm-customer-workflow"),
                                List.of(),
                                List.of(),
                                "No GitLab project candidates found for hints: [crm-customer-workflow]"
                        )
                ));

        mockMvc.perform(post("/api/gitlab/repository/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectHints": ["crm-customer-workflow"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.group").value("CRM"))
                .andExpect(jsonPath("$.message").value("No GitLab project candidates found for hints: [crm-customer-workflow]"));
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

    @Test
    void shouldReturnBadRequestForInvalidFilesByPathRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/files/by-path")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "",
                                  "projectName": "",
                                  "branch": "",
                                  "filePaths": [],
                                  "maxCharactersPerFile": 0,
                                  "maxTotalCharacters": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabRepositoryFilesByPathApiService);
    }

}
