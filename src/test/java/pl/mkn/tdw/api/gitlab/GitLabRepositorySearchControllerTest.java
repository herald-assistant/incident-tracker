package pl.mkn.tdw.api.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchException;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchService;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseFileRole;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseRelationKind;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseRepositoryContext;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextLimits;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseEntryCandidate;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseEntryMethod;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseEntryStatus;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaTypeKind;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private GitLabJavaMethodUseCaseContextService gitLabJavaMethodUseCaseContextService;

    @MockitoBean
    private GitLabRepositoryFilesByPathApiService gitLabRepositoryFilesByPathApiService;

    @MockitoBean
    private GitLabJavaMethodSliceService gitLabJavaMethodSliceService;

    @MockitoBean
    private GitLabOpenApiEndpointSliceService gitLabOpenApiEndpointSliceService;

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
                                "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                                17,
                                19,
                                List.of("@PathVariable String customerId"),
                                List.of("ResponseEntity<CustomerProfileResponse>"),
                                List.of("RestController", "GetMapping"),
                                "high",
                                List.of(),
                                List.of("crm-customer-api:src/main/java/com/example/crm/customer/CustomerProfileController.java lines 17-19 via gitlab_read_repository_file_chunk")
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
                                "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                                17,
                                19,
                                List.of("@PathVariable String customerId"),
                                List.of("CustomerProfileResponse"),
                                List.of("RestController", "GetMapping"),
                                GitLabEndpointUseCaseConfidence.HIGH,
                                List.of(),
                                List.of()
                        ),
                        List.of(new GitLabEndpointUseCaseFileCandidate(
                                "src/main/java/com/example/crm/customer/CustomerProfileController.java",
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
                                "Endpoint handler resolved this CRM customer profile method."
                        )),
                        List.of(),
                        List.of(),
                        List.of("crm-customer-api:src/main/java/com/example/crm/customer/CustomerProfileController.java via gitlab_read_repository_file_outline"),
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
    void shouldBuildJavaMethodUseCaseContextForValidRequest() throws Exception {
        when(gitLabJavaMethodUseCaseContextService.buildContext(
                eq("CRM"),
                eq("release-candidate"),
                any(GitLabJavaMethodUseCaseContextRequest.class)
        )).thenReturn(new GitLabJavaMethodUseCaseContextResult(
                new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-customer-service", "release-candidate"),
                new GitLabJavaMethodUseCaseEntryMethod(
                        GitLabJavaMethodUseCaseEntryStatus.RESOLVED,
                        "com.example.crm.customer.application.CustomerCaseService",
                        "registerCase",
                        "src/main/java/com/example/crm/customer/application/CustomerCaseService.java",
                        "CustomerCaseService",
                        "CustomerCaseService",
                        "com.example.crm.customer.application.CustomerCaseService",
                        GitLabJavaTypeKind.CLASS,
                        "registerCase",
                        "CustomerCaseResult registerCase(CustomerCaseForm form)",
                        17,
                        21,
                        1,
                        List.of("CustomerCaseForm"),
                        List.of("form"),
                        "CustomerCaseResult",
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(new GitLabJavaMethodUseCaseEntryCandidate(
                                "src/main/java/com/example/crm/customer/application/CustomerCaseService.java",
                                "CustomerCaseService",
                                "CustomerCaseService",
                                "com.example.crm.customer.application.CustomerCaseService",
                                GitLabJavaTypeKind.CLASS,
                                "registerCase",
                                "CustomerCaseResult registerCase(CustomerCaseForm form)",
                                17,
                                21,
                                1,
                                List.of("CustomerCaseForm"),
                                List.of("form"),
                                "CustomerCaseResult",
                                GitLabEndpointUseCaseConfidence.HIGH,
                                "Resolved entry method."
                        )),
                        List.of()
                ),
                List.of(new GitLabEndpointUseCaseFileCandidate(
                        "src/main/java/com/example/crm/customer/application/CustomerCaseService.java",
                        GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                        2,
                        List.of("registerCase"),
                        "Traversal starts from Java method entry point.",
                        GitLabEndpointUseCaseConfidence.HIGH
                )),
                List.of(new GitLabEndpointUseCaseRelation(
                        "com.example.crm.customer.application.CustomerCaseService#registerCase",
                        "com.example.crm.customer.domain.CustomerCaseRepositoryPort#save",
                        GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL,
                        GitLabEndpointUseCaseConfidence.HIGH,
                        "Method call on injected dependency repositoryPort."
                )),
                List.of(),
                List.of(),
                List.of("crm-customer-service:src/main/java/com/example/crm/customer/application/CustomerCaseService.java via gitlab_read_repository_file_outline"),
                new GitLabJavaMethodUseCaseContextLimits(5, 20, 60, false, false, 4, false),
                GitLabEndpointUseCaseConfidence.HIGH
        ));

        mockMvc.perform(post("/api/gitlab/repository/java-method-use-case-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM",
                                  "projectName": "crm-customer-service",
                                  "branch": "release-candidate",
                                  "filePath": "src/main/java/com/example/crm/customer/application/CustomerCaseService.java",
                                  "className": "com.example.crm.customer.application.CustomerCaseService",
                                  "methodName": "registerCase",
                                  "lineNumber": 18,
                                  "parameterCount": 1,
                                  "parameterTypes": ["CustomerCaseForm"],
                                  "maxDepth": 5,
                                  "maxResults": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.group").value("CRM"))
                .andExpect(jsonPath("$.entryMethod.status").value("RESOLVED"))
                .andExpect(jsonPath("$.entryMethod.methodName").value("registerCase"))
                .andExpect(jsonPath("$.entryMethod.lineStart").value(17))
                .andExpect(jsonPath("$.files[0].role").value("USE_CASE_SERVICE"))
                .andExpect(jsonPath("$.relations[0].kind").value("INJECTED_PORT_CALL"))
                .andExpect(jsonPath("$.limits.maxResults").value(20))
                .andExpect(jsonPath("$.confidence").value("HIGH"));

        verify(gitLabJavaMethodUseCaseContextService).buildContext(
                eq("CRM"),
                eq("release-candidate"),
                argThat(request -> "crm-customer-service".equals(request.projectName())
                        && "src/main/java/com/example/crm/customer/application/CustomerCaseService.java".equals(request.filePath())
                        && "com.example.crm.customer.application.CustomerCaseService".equals(request.className())
                        && "registerCase".equals(request.methodName())
                        && request.lineNumber() == 18
                        && request.parameterCount() == 1
                        && request.parameterTypes().equals(List.of("CustomerCaseForm"))
                        && request.maxDepth() == 5
                        && request.maxResults() == 20)
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
                                        "src/main/java/com/example/crm/customer/CustomerProfileController.java",
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
                                    "src/main/java/com/example/crm/customer/CustomerProfileController.java",
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
                .andExpect(jsonPath("$.files[0].filePath").value("src/main/java/com/example/crm/customer/CustomerProfileController.java"))
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
    void shouldReadOpenApiEndpointSliceForValidRequest() throws Exception {
        when(gitLabOpenApiEndpointSliceService.readEndpointSlice(any(GitLabOpenApiEndpointSliceRequest.class)))
                .thenReturn(new GitLabOpenApiEndpointSliceResponse(
                        "CRM",
                        "crm-customer-api",
                        "release-candidate",
                        "src/main/resources/openapi/customer-api.yml",
                        "OK",
                        "openapi",
                        "3.0.3",
                        "POST",
                        "/api/customers/{customerId}/cases",
                        "/api/customers/{id}/cases",
                        "createCustomerCase",
                        "Create CRM customer case",
                        "Creates a CRM case for an existing customer.",
                        List.of("Customer Cases"),
                        "crm-customer-api:src/main/resources/openapi/customer-api.yml#POST /api/customers/{id}/cases",
                        "# OpenAPI endpoint contract\n\n```json\n{\"operationId\":\"createCustomerCase\"}\n```",
                        82,
                        false,
                        List.of()
                ));

        mockMvc.perform(post("/api/gitlab/repository/openapi-endpoint-slice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "CRM",
                                  "projectName": "crm-customer-api",
                                  "branch": "release-candidate",
                                  "filePath": "src/main/resources/openapi/customer-api.yml",
                                  "httpMethod": "POST",
                                  "endpointPath": "/api/customers/{customerId}/cases",
                                  "includeReferencedSchemas": true,
                                  "schemaDepth": 2,
                                  "maxCharacters": 20000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("CRM"))
                .andExpect(jsonPath("$.projectName").value("crm-customer-api"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.specType").value("openapi"))
                .andExpect(jsonPath("$.matchedPath").value("/api/customers/{id}/cases"))
                .andExpect(jsonPath("$.operationId").value("createCustomerCase"))
                .andExpect(jsonPath("$.sourceRef").value("crm-customer-api:src/main/resources/openapi/customer-api.yml#POST /api/customers/{id}/cases"));

        verify(gitLabOpenApiEndpointSliceService).readEndpointSlice(argThat(request ->
                "CRM".equals(request.group())
                        && "crm-customer-api".equals(request.projectName())
                        && "release-candidate".equals(request.branch())
                        && "src/main/resources/openapi/customer-api.yml".equals(request.filePath())
                        && "POST".equals(request.httpMethod())
                        && "/api/customers/{customerId}/cases".equals(request.endpointPath())
                        && Boolean.TRUE.equals(request.includeReferencedSchemas())
                        && request.schemaDepth() == 2
                        && request.maxCharacters() == 20000
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
    void shouldReturnBadRequestForInvalidJavaMethodUseCaseContextRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/java-method-use-case-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "",
                                  "projectName": "",
                                  "branch": "",
                                  "className": "",
                                  "methodName": "",
                                  "lineNumber": 0,
                                  "parameterCount": -1,
                                  "maxDepth": 99,
                                  "maxResults": 999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabJavaMethodUseCaseContextService);
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

    @Test
    void shouldReturnBadRequestForInvalidOpenApiEndpointSliceRequest() throws Exception {
        mockMvc.perform(post("/api/gitlab/repository/openapi-endpoint-slice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "group": "",
                                  "projectName": "",
                                  "branch": "",
                                  "filePath": "",
                                  "httpMethod": "",
                                  "endpointPath": "",
                                  "schemaDepth": 99,
                                  "maxCharacters": 999999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(gitLabOpenApiEndpointSliceService);
    }

}
