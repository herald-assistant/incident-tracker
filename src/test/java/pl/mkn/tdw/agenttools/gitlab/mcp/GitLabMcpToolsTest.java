package pl.mkn.tdw.agenttools.gitlab.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceMethodSelector;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
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
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseEntryMethod;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseEntryStatus;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaTypeKind;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFileChunkRequest;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaMethodSummary;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaFieldSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.mkn.tdw.testsupport.agenttools.GitLabMcpToolsTestCreator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GitLabMcpToolsTest {

    private static final String DEFAULT_GROUP = "CRM/backend";
    private static final String DEFAULT_BRANCH_REF = "feature/INC-123";
    private static final String DEFAULT_APPLICATION_NAME = "backend";

    private final GitLabMcpTools gitLabMcpTools = GitLabMcpToolsTestCreator.create(
            new TestGitLabRepositoryPort(),
            gitLabProperties(DEFAULT_GROUP)
    );

    @Test
    void shouldListAvailableRepositoriesFromOperationalContextUsingSessionGroup() {
        var tools = GitLabMcpToolsTestCreator.create(
                mock(GitLabRepositoryPort.class),
                ignored -> operationalContextCatalog(
                        List.of(codeSearchScope(
                                "backend-component-code-search",
                                "system",
                                "backend",
                                List.of(
                                        scopeRepository("backend-repo", "primary", 1),
                                        scopeRepository("customer-repo", "collaborator", 2)
                                )
                        )),
                        repository(
                                "backend-repo",
                                "Backend",
                                "service",
                                "active",
                                "CRM",
                                "CRM/backend",
                                "backend",
                                List.of("backend", "CRM/backend"),
                                List.of("backend"),
                                List.of("decision", "limit"),
                                List.of("decision-process"),
                                List.of("cbp"),
                                List.of("shared-lib-repo")
                        ),
                        repository(
                                "customer-repo",
                                "Customer",
                                "service",
                                "active",
                                "CRM/PROCESSES",
                                "CRM/PROCESSES/CRM_CUSTOMER_PROCESS",
                                "CRM_CUSTOMER_PROCESS",
                                List.of("customer-process"),
                                List.of("customer"),
                                List.of("customer"),
                                List.of("customer-process"),
                                List.of(),
                                List.of()
                        ),
                        repository(
                                "outside-repo",
                                "Outside",
                                "service",
                                "active",
                                "OTHER",
                                "OTHER/outside",
                                "outside",
                                List.of("outside"),
                                List.of("outside"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                ),
                gitLabProperties("CRM")
        );

        var response = tools.listAvailableRepositories(
                DEFAULT_APPLICATION_NAME,
                "release/2026.04",
                "Sprawdzam katalog repozytoriow.",
                gitLabToolContext()
        );

        assertEquals("CRM", response.group());
        assertEquals("release/2026.04", response.branch());
        assertEquals(2, response.repositories().size());
        assertEquals(1, response.codeSearchScopes().size());

        var backend = response.repositories().get(0);
        assertEquals("backend-repo", backend.repositoryId());
        assertEquals("Backend", backend.name());
        assertEquals("backend", backend.projectName());
        assertEquals("CRM/backend", backend.gitLabPath());
        assertTrue(backend.summary().contains("Backend repository"));
        assertTrue(backend.summary().contains("Bounded contexts: decision, limit."));
        assertIterableEquals(List.of("backend-repo", "Backend", "backend", "CRM/backend"), backend.aliases());
        assertEquals("service", backend.repositoryType());
        assertEquals("active", backend.lifecycleStatus());
        assertIterableEquals(List.of("backend"), backend.systems());
        assertIterableEquals(List.of("decision", "limit"), backend.boundedContexts());
        assertIterableEquals(List.of("decision-process"), backend.processes());
        assertIterableEquals(List.of("cbp"), backend.integrations());
        assertIterableEquals(List.of("shared-lib-repo"), backend.relatedRepositoryIds());

        var customer = response.repositories().get(1);
        assertEquals("PROCESSES/CRM_CUSTOMER_PROCESS", customer.projectName());
        assertEquals("CRM/PROCESSES/CRM_CUSTOMER_PROCESS", customer.gitLabPath());

        var scope = response.codeSearchScopes().get(0);
        assertEquals("backend-component-code-search", scope.scopeId());
        assertEquals("system", scope.target().type());
        assertEquals("backend", scope.target().id());
        assertIterableEquals(
                List.of("backend", "PROCESSES/CRM_CUSTOMER_PROCESS"),
                scope.projectNames()
        );
        assertEquals("primary", scope.repositories().get(0).role());
        assertEquals("collaborator", scope.repositories().get(1).role());
        assertTrue(scope.limitations().contains("Generated clients are partial in this fixture."));
    }

    @Test
    void shouldDelegateSearchRepositoryCandidatesUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                "Matched timeout-related service and log keywords.",
                95
        )));

        var response = tools.searchRepositoryCandidates(
                List.of("crm-customer-profile-service", "crm-customer-segment-service"),
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                List.of("GET /crm/customers"),
                List.of("timeout", "customer-profile"),
                "Sprawdzam kandydatow repozytorium dla testu.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                query.correlationId() == null
                        && "CRM/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("crm-customer-profile-service", "crm-customer-segment-service").equals(query.projectNames())
                        && List.of("GET /crm/customers").equals(query.operationNames())
                        && List.of("timeout", "customer-profile").equals(query.keywords())
        ));
        assertEquals(1, response.candidates().size());
        assertEquals("crm-customer-client-service", response.candidates().get(0).projectName());
    }

    @Test
    void shouldDefaultNullListsToEmptyListsInSearchRepositoryCandidates() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of());

        tools.searchRepositoryCandidates(
                null,
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                null,
                null,
                "Sprawdzam puste wejscie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                query.projectNames().isEmpty()
                        && query.operationNames().isEmpty()
                        && query.keywords().isEmpty()
        ));
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughToolUsingComponentHintForNestedProject() {
        var tools = GitLabMcpToolsTestCreator.create(new TestGitLabRepositoryPort(), gitLabProperties("CRM"));

        var response = tools.searchRepositoryCandidates(
                List.of("crm-customer-workflow"),
                "release-candidate",
                "customer",
                List.of(),
                List.of("customer-profile"),
                "Sprawdzam zagniezdzony projekt.",
                gitLabToolContext()
        );

        assertEquals(1, response.candidates().size());
        assertEquals("CRM", response.candidates().get(0).group());
        assertEquals("CRM_WORKFLOWS/CUSTOMER_WORKFLOW", response.candidates().get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/CustomerWorkflowService.java", response.candidates().get(0).filePath());
    }

    @Test
    void shouldListRepositoryEndpointsUsingSessionBoundScope() {
        var response = gitLabMcpTools.listRepositoryEndpoints(
                "crm-customer-api",
                "feature/FLOW-1",
                DEFAULT_APPLICATION_NAME,
                "/api/customers",
                "GET",
                50,
                "Listuje endpointy profilu klienta CRM.",
                gitLabToolContext()
        );

        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/FLOW-1", response.branch());
        assertEquals("/api/customers", response.endpointPathPrefix());
        assertEquals("GET", response.httpMethod());
        assertEquals(1, response.candidateFileCount());
        assertEquals(1, response.scannedFileCount());
        assertFalse(response.scannedFileLimitReached());
        assertEquals(1, response.endpoints().size());

        var endpoint = response.endpoints().get(0);
        assertEquals("/api/customers/{customerId}", endpoint.path());
        assertIterableEquals(List.of("GET"), endpoint.httpMethods());
        assertEquals("com.example.crm.customer.api.CustomerController", endpoint.controllerClass());
        assertEquals("getCustomer", endpoint.handlerMethod());
        assertEquals("src/main/java/com/example/crm/customer/api/CustomerProfileController.java", endpoint.filePath());
        assertIterableEquals(List.of("@PathVariable String customerId"), endpoint.requestTypes());
        assertIterableEquals(List.of("ResponseEntity<CustomerProfileResponse>"), endpoint.responseTypes());
        assertTrue(endpoint.annotations().contains("RestController"));
        assertTrue(endpoint.annotations().contains("GetMapping"));
        assertEquals("high", endpoint.confidence());
        assertTrue(endpoint.limitations().isEmpty());
        assertTrue(endpoint.suggestedNextReads().get(0).contains("gitlab_read_repository_file_chunk"));
    }

    @Test
    void shouldBuildEndpointUseCaseContextUsingSessionBoundScope() {
        var endpointUseCaseContextService = mock(GitLabEndpointUseCaseContextService.class);
        var tools = GitLabMcpToolsTestCreator.create(
                mock(GitLabRepositoryPort.class),
                ignored -> OperationalContextCatalog.empty(),
                mock(GitLabRepositoryEndpointService.class),
                endpointUseCaseContextService,
                mock(GitLabJavaMethodUseCaseContextService.class),
                mock(GitLabJavaMethodSliceService.class),
                new GitLabOpenApiEndpointSliceService(mock(GitLabRepositoryPort.class), new ObjectMapper()),
                gitLabProperties("CRM/backend")
        );
        when(endpointUseCaseContextService.buildContext(any(), any(), any()))
                .thenReturn(new GitLabEndpointUseCaseContextResult(
                        new GitLabEndpointUseCaseRepositoryContext(
                                "CRM/backend",
                                "crm-customer-api",
                                "feature/FLOW-1"
                        ),
                        new GitLabEndpointUseCaseEndpointContext(
                                "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                                List.of("GET"),
                                "/api/customers/{customerId}",
                                "/api/customers/{customerId}",
                                "com.example.crm.customer.CustomerController",
                                "getCustomer",
                                "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                                10,
                                18,
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

        var response = tools.buildEndpointUseCaseContext(
                "crm-customer-api",
                "feature/FLOW-1",
                DEFAULT_APPLICATION_NAME,
                "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                null,
                null,
                4,
                12,
                "Buduje liste plikow dla endpointu profilu klienta CRM.",
                gitLabToolContext()
        );

        verify(endpointUseCaseContextService).buildContext(
                eq("CRM/backend"),
                eq("feature/FLOW-1"),
                argThat(request -> "crm-customer-api".equals(request.projectName())
                        && "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer".equals(request.endpointId())
                        && request.httpMethod() == null
                        && request.endpointPath() == null
                        && request.maxDepth() == 4
                        && request.maxFiles() == 12)
        );
        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/FLOW-1", response.branch());
        assertEquals("getCustomer", response.endpoint().handlerMethod());
        assertEquals(1, response.files().size());
        assertEquals(GitLabEndpointUseCaseFileRole.CONTROLLER, response.files().get(0).role());
        assertEquals(1, response.relations().size());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, response.confidence());
        assertTrue(response.suggestedNextReads().get(0).contains("gitlab_read_repository_file_outline"));
    }

    @Test
    void shouldBuildJavaMethodUseCaseContextUsingSessionBoundScopeAndMaxResults() {
        var javaMethodUseCaseContextService = mock(GitLabJavaMethodUseCaseContextService.class);
        var tools = GitLabMcpToolsTestCreator.create(
                mock(GitLabRepositoryPort.class),
                ignored -> OperationalContextCatalog.empty(),
                mock(GitLabRepositoryEndpointService.class),
                mock(GitLabEndpointUseCaseContextService.class),
                javaMethodUseCaseContextService,
                mock(GitLabJavaMethodSliceService.class),
                new GitLabOpenApiEndpointSliceService(mock(GitLabRepositoryPort.class), new ObjectMapper()),
                gitLabProperties("CRM/backend")
        );
        when(javaMethodUseCaseContextService.buildContext(any(), any(), any()))
                .thenReturn(new GitLabJavaMethodUseCaseContextResult(
                        new GitLabEndpointUseCaseRepositoryContext(
                                "CRM/backend",
                                "crm-customer-api",
                                "feature/FLOW-1"
                        ),
                        new GitLabJavaMethodUseCaseEntryMethod(
                                GitLabJavaMethodUseCaseEntryStatus.RESOLVED,
                                "com.example.crm.customer.CustomerService",
                                "getCustomer",
                                "src/main/java/com/example/crm/customer/CustomerService.java",
                                "CustomerService",
                                "CustomerService",
                                "com.example.crm.customer.CustomerService",
                                GitLabJavaTypeKind.CLASS,
                                "getCustomer",
                                "CustomerModel getCustomer(CustomerId customerId)",
                                42,
                                50,
                                1,
                                List.of("CustomerId"),
                                List.of("customerId"),
                                "CustomerModel",
                                GitLabEndpointUseCaseConfidence.HIGH,
                                List.of(),
                                List.of()
                        ),
                        List.of(new GitLabEndpointUseCaseFileCandidate(
                                "src/main/java/com/example/crm/customer/CustomerService.java",
                                GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                                1,
                                List.of("getCustomer"),
                                "Entry method selected by class and method.",
                                GitLabEndpointUseCaseConfidence.HIGH
                        )),
                        List.of(new GitLabEndpointUseCaseRelation(
                                "com.example.crm.customer.CustomerService#getCustomer",
                                "com.example.crm.customer.CustomerRepository#findCustomer",
                                GitLabEndpointUseCaseRelationKind.REPOSITORY_CALL,
                                GitLabEndpointUseCaseConfidence.HIGH,
                                "Entry method calls repository dependency."
                        )),
                        List.of(),
                        List.of(),
                        List.of("crm-customer-api:src/main/java/com/example/crm/customer/CustomerService.java via gitlab_read_java_method_slice"),
                        new GitLabJavaMethodUseCaseContextLimits(
                                4,
                                12,
                                80,
                                false,
                                false,
                                2,
                                false
                        ),
                        GitLabEndpointUseCaseConfidence.HIGH
                ));

        var response = tools.buildJavaMethodUseCaseContext(
                "crm-customer-api",
                "feature/FLOW-1",
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/crm/customer/CustomerService.java",
                "com.example.crm.customer.CustomerService",
                "getCustomer",
                44,
                1,
                List.of("CustomerId"),
                4,
                12,
                "Kontynuuje flow CRM od metody serwisu.",
                gitLabToolContext()
        );

        verify(javaMethodUseCaseContextService).buildContext(
                eq("CRM/backend"),
                eq("feature/FLOW-1"),
                argThat(request -> "crm-customer-api".equals(request.projectName())
                        && "src/main/java/com/example/crm/customer/CustomerService.java".equals(request.filePath())
                        && "com.example.crm.customer.CustomerService".equals(request.className())
                        && "getCustomer".equals(request.methodName())
                        && request.lineNumber() == 44
                        && request.parameterCount() == 1
                        && request.parameterTypes().equals(List.of("CustomerId"))
                        && request.maxDepth() == 4
                        && request.maxResults() == 12)
        );
        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/FLOW-1", response.branch());
        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, response.entryMethod().status());
        assertEquals("getCustomer", response.entryMethod().methodName());
        assertEquals(1, response.files().size());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE, response.files().get(0).role());
        assertEquals(1, response.relations().size());
        assertEquals(12, response.limits().maxResults());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, response.confidence());
        assertTrue(response.suggestedNextReads().get(0).contains("gitlab_read_java_method_slice"));
    }

    @Test
    void shouldDelegateReadRepositoryFileUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                120
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                "class CustomerProfileClient {}",
                false
        ));

        var response = tools.readRepositoryFile(
                "crm-customer-client-service",
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                120,
                "Czytam plik w tescie delegacji.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                120
        );
        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-client-service", response.projectName());
        assertEquals("feature/INC-123", response.branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CustomerProfileClient.java", response.filePath());
        assertEquals("class CustomerProfileClient {}", response.content());
        assertFalse(response.truncated());
    }

    @Test
    void shouldResolveNestedOperationalContextRepositoryGroupUnderConfiguredRootGroup() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = GitLabMcpToolsTestCreator.create(
                gitLabRepositoryPort,
                ignored -> operationalContextCatalog(repository(
                        "crm-customer-process-repo",
                        "CRM Customer Process",
                        "service",
                        "active",
                        "CRM/PROCESSES",
                        "CRM/PROCESSES/CRM_CUSTOMER_PROCESS",
                        "CRM_CUSTOMER_PROCESS",
                        List.of("customer-process"),
                        List.of("customer-process"),
                        List.of("customer"),
                        List.of("customer-process"),
                        List.of(),
                        List.of()
                )),
                gitLabProperties("CRM")
        );
        when(gitLabRepositoryPort.readFile(
                "CRM",
                "PROCESSES/CRM_CUSTOMER_PROCESS",
                "main",
                "src/main/java/com/example/crm/customer/CustomerProcessController.java",
                120
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "PROCESSES/CRM_CUSTOMER_PROCESS",
                "main",
                "src/main/java/com/example/crm/customer/CustomerProcessController.java",
                "class CustomerProcessController {}",
                false
        ));

        var response = tools.readRepositoryFile(
                "PROCESSES/CRM_CUSTOMER_PROCESS",
                "main",
                "customer-process",
                "src/main/java/com/example/crm/customer/CustomerProcessController.java",
                120,
                "Czytam repozytorium z podgrupy GitLaba.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "CRM",
                "PROCESSES/CRM_CUSTOMER_PROCESS",
                "main",
                "src/main/java/com/example/crm/customer/CustomerProcessController.java",
                120
        );
        assertEquals("CRM", response.group());
        assertEquals("PROCESSES/CRM_CUSTOMER_PROCESS", response.projectName());
    }

    @Test
    void shouldUseDefaultCharacterLimitWhenNotProvided() {
        var tools = GitLabMcpToolsTestCreator.create(new TestGitLabRepositoryPort(), gitLabProperties("CRM/runtime"));

        var response = tools.readRepositoryFile(
                "crm-customer-account-service",
                "release/2026.04",
                "crm-runtime",
                "src/main/java/com/example/crm/customer/account/CustomerAccountService.java",
                null,
                "Czytam plik z domyslnym limitem.",
                gitLabToolContext()
        );

        assertFalse(response.truncated());
        assertTrue(response.content().contains("CustomerAccountService"));
        assertEquals("CRM/runtime", response.group());
        assertEquals("release/2026.04", response.branch());
    }

    @Test
    void shouldReadJavaMethodSliceUsingSessionScope() {
        var response = gitLabMcpTools.readJavaMethodSlice(
                "crm-customer-api",
                "feature/FLOW-1",
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/crm/customer/api/CustomerProfileController.java",
                "CustomerController",
                List.of(new GitLabJavaMethodSliceMethodSelector("getCustomer", null)),
                true,
                true,
                true,
                8_000,
                "Czytam wycinek metody kontrolera.",
                gitLabToolContext()
        );

        assertEquals("OK", response.status());
        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/FLOW-1", response.branch());
        assertEquals(List.of(new GitLabJavaMethodSliceMethodSelector("getCustomer", null)), response.requestedMethods());
        assertTrue(response.content().contains("public ResponseEntity<CustomerProfileResponse> getCustomer"));
        assertFalse(response.content().contains("public CustomerProfileResponse updateCustomerProfile"));
    }

    @Test
    void shouldReadRepositoryFilesByPathUsingSessionScopeAndRespectLimits() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/MissingCustomerService.java",
                10
        )).thenThrow(new IllegalStateException("file not found"));
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                10
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                "1234567890",
                false
        ));
        when(gitLabRepositoryPort.readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                "blob-service",
                "commit-service",
                "last-service",
                "2026-06-15T11:30:00.000Z",
                "sha-service",
                5678L
        ));
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                5
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                "abcde",
                true
        ));
        when(gitLabRepositoryPort.readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                "blob-repository",
                "commit-repository",
                "last-repository",
                "2026-06-16T09:00:00.000Z",
                "sha-repository",
                4321L
        ));

        var response = tools.readRepositoryFilesByPath(
                "crm-customer-api",
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                List.of(
                        "/src/main/java/com/example/crm/customer/MissingCustomerService.java",
                        "crm-customer-api:src/main/java/com/example/crm/customer/CustomerService.java via gitlab_read_repository_file_outline",
                        "\\src\\main\\java\\com\\example\\crm\\customer\\CustomerProfileRepository.java",
                        "src/main/java/com/example/crm/customer/CustomerProfileMapper.java"
                ),
                10,
                15,
                "Czytam liste plikow z kontekstu endpointu.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/MissingCustomerService.java",
                10
        );
        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java",
                10
        );
        verify(gitLabRepositoryPort).readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerService.java"
        );
        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                5
        );
        verify(gitLabRepositoryPort).readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerProfileRepository.java"
        );
        verifyNoMoreInteractions(gitLabRepositoryPort);

        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/INC-123", response.branch());
        assertEquals(4, response.requestedFileCount());
        assertEquals(3, response.processedFileCount());
        assertEquals(2, response.returnedFileCount());
        assertEquals(1, response.failedFileCount());
        assertEquals(15, response.totalReturnedCharacters());
        assertFalse(response.fileCountTruncated());
        assertTrue(response.totalCharacterLimitReached());
        assertEquals(3, response.files().size());
        assertTrue(response.files().get(0).error().contains("file not found"));
        assertEquals("SKIPPED", response.files().get(0).metadataStatus());
        assertEquals("service-or-orchestrator", response.files().get(1).inferredRole());
        assertEquals("last-service", response.files().get(1).lastCommitId());
        assertEquals("2026-06-15T11:30:00.000Z", response.files().get(1).lastModifiedAt());
        assertEquals("repository", response.files().get(2).inferredRole());
        assertEquals(4321L, response.files().get(2).sizeBytes());
        assertTrue(response.files().get(2).truncated());
    }

    @Test
    void shouldDelegateReadRepositoryFileChunkUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFileChunk(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                5,
                12,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                5,
                12,
                5,
                12,
                14,
                "return customerProfileWebClient.get();",
                false
        ));

        var response = tools.readRepositoryFileChunk(
                "crm-customer-client-service",
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                5,
                12,
                4_000,
                "Czytam fragment pliku w tescie delegacji.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                5,
                12,
                4_000
        );
        assertEquals(5, response.requestedStartLine());
        assertEquals(12, response.requestedEndLine());
        assertEquals(5, response.returnedStartLine());
        assertEquals(12, response.returnedEndLine());
        assertEquals(14, response.totalLines());
        assertEquals("return customerProfileWebClient.get();", response.content());
        assertFalse(response.truncated());
    }

    @Test
    void shouldReadRepositoryFileOutlineAndInferRole() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                30_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                """
                        package com.example.crm.customerprofile;

                        import java.util.List;
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
                        import org.springframework.transaction.annotation.Transactional;

                        @Service
                        public class CustomerProfileService {
                            private final CustomerPolicy customerPolicy;
                            @Deprecated
                            private NotificationPort notificationPort;

                            @Autowired
                            public CustomerProfileService(CustomerPolicy customerPolicy) {
                                this.customerPolicy = customerPolicy;
                            }

                            @Transactional
                            public CustomerProfileResult processCustomerProfile(String customerId) {
                                return CustomerProfileResult.success(customerId);
                            }

                            private void validate(Customer customer) {
                            }
                        }
                        """,
                false
        ));

        var response = tools.readRepositoryFileOutline(
                "crm-customer-profile-service",
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                null,
                "Sprawdzam zarys pliku w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                30_000
        );
        assertEquals("com.example.crm.customerprofile", response.packageName());
        assertIterableEquals(
                List.of(
                        "import java.util.List;",
                        "import org.springframework.beans.factory.annotation.Autowired;",
                        "import org.springframework.stereotype.Service;",
                        "import org.springframework.transaction.annotation.Transactional;"
                ),
                response.imports()
        );
        assertEquals(1, response.typeSummaries().size());
        var typeSummary = response.typeSummaries().get(0);
        assertEquals("CustomerProfileService", typeSummary.name());
        assertTrue(typeSummary.qualifiedName().endsWith("CustomerProfileService"));
        assertEquals("class", typeSummary.kind());
        assertEquals("public class CustomerProfileService {", typeSummary.declaration());
        assertIterableEquals(List.of("public"), typeSummary.modifiers());
        assertIterableEquals(List.of("@Service"), typeSummary.annotations());
        assertTrue(typeSummary.lineStart() > 0);
        assertTrue(typeSummary.lineEnd() >= typeSummary.lineStart());
        assertIterableEquals(
                List.of("customerPolicy", "notificationPort"),
                response.fieldSummaries().stream().map(GitLabJavaFieldSummary::name).toList()
        );
        var policyField = response.fieldSummaries().get(0);
        assertTrue(policyField.declaringTypeName().endsWith("CustomerProfileService"));
        assertEquals("CustomerPolicy", policyField.type());
        assertIterableEquals(List.of("private", "final"), policyField.modifiers());
        assertTrue(policyField.annotations().isEmpty());
        assertTrue(policyField.lineStart() > 0);
        assertTrue(policyField.lineEnd() >= policyField.lineStart());
        var notificationPortField = response.fieldSummaries().get(1);
        assertEquals("NotificationPort", notificationPortField.type());
        assertIterableEquals(List.of("private"), notificationPortField.modifiers());
        assertIterableEquals(List.of("@Deprecated"), notificationPortField.annotations());
        assertEquals(1, response.constructorSummaries().size());
        var constructorSummary = response.constructorSummaries().get(0);
        assertTrue(constructorSummary.declaringTypeName().endsWith("CustomerProfileService"));
        assertEquals("public CustomerProfileService(CustomerPolicy customerPolicy)", constructorSummary.signature());
        assertIterableEquals(List.of("public"), constructorSummary.modifiers());
        assertIterableEquals(List.of("@Autowired"), constructorSummary.annotations());
        assertTrue(constructorSummary.lineStart() > 0);
        assertTrue(constructorSummary.lineEnd() >= constructorSummary.lineStart());
        assertIterableEquals(
                List.of(
                        "public CustomerProfileResult processCustomerProfile(String customerId)",
                        "private void validate(Customer customer)"
                ),
                response.methodSummaries().stream().map(GitLabJavaMethodSummary::signature).toList()
        );
        var processCustomerProfileSummary = response.methodSummaries().get(0);
        assertIterableEquals(List.of("public"), processCustomerProfileSummary.modifiers());
        assertIterableEquals(List.of("@Transactional"), processCustomerProfileSummary.annotations());
        var validateSummary = response.methodSummaries().get(1);
        assertIterableEquals(List.of("private"), validateSummary.modifiers());
        assertTrue(validateSummary.annotations().isEmpty());
        assertEquals("service-or-orchestrator", response.inferredRole());
        assertFalse(response.truncated());
    }

    @Test
    void shouldReadRepositoryFileChunksAndRespectBatchAndCharacterLimits() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                10,
                20,
                10
        )).thenReturn(new GitLabRepositoryFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                10,
                20,
                10,
                20,
                120,
                "1234567890",
                false
        ));
        when(gitLabRepositoryPort.readFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileRepository.java",
                30,
                40,
                5
        )).thenReturn(new GitLabRepositoryFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileRepository.java",
                30,
                40,
                30,
                34,
                80,
                "abcde",
                true
        ));

        var response = tools.readRepositoryFileChunks(
                List.of(
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java", 10, 20, 10),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileRepository.java", 30, 40, 10),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileMapper.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileValidator.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/NotificationClient.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileController.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/OutboxPublisher.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/CustomerProfileJob.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-customer-profile-service", "src/main/java/com/example/crm/customerprofile/Extra.java", 1, 5, 100)
                ),
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                15,
                "Czytam kilka fragmentow w tescie limitow.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileService.java",
                10,
                20,
                10
        );
        verify(gitLabRepositoryPort).readFileChunk(
                "CRM/backend",
                "crm-customer-profile-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/customerprofile/CustomerProfileRepository.java",
                30,
                40,
                5
        );
        verifyNoMoreInteractions(gitLabRepositoryPort);

        assertEquals(2, response.chunks().size());
        assertEquals("CRM/backend", response.group());
        assertEquals("feature/INC-123", response.branch());
        assertTrue(response.chunkCountTruncated());
        assertTrue(response.totalCharacterLimitReached());
        assertEquals("service-or-orchestrator", response.chunks().get(0).inferredRole());
        assertEquals("repository", response.chunks().get(1).inferredRole());
    }

    @Test
    void shouldFindFlowContextGroupedByRoleAndUseFocusedKeywordsFromSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-api",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                        "Controller handling submitCustomer endpoint.",
                        100
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerService.java",
                        "Service orchestrates submitCustomer flow.",
                        95
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                        "Repository predicate used in submitCustomerProfile.",
                        92
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/LegacyCustomerProfileRepository.java",
                        "Fallback repository path.",
                        80
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerProfileMapper.java",
                        "Mapper between API and domain model.",
                        75
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-client",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/NotificationClient.java",
                        "HTTP client calling notification system.",
                        70
                )
        ));

        var response = tools.findFlowContext(
                List.of("crm-customer-api", "crm-customer-core"),
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                List.of(
                        "com.example.crm.customer.CustomerController",
                        "CustomerController",
                        "import com.example.crm.customer.CustomerController;",
                        "createCustomer",
                        "timeout",
                        "repository"
                ),
                List.of("POST /crm/customers"),
                1,
                "Szukam kontekstu przeplywu w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                query.correlationId() == null
                        && "CRM/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("crm-customer-api", "crm-customer-core").equals(query.projectNames())
                        && List.of("POST /crm/customers").equals(query.operationNames())
                        && List.of(
                        "com.example.crm.customer.CustomerController",
                        "CustomerController",
                        "import com.example.crm.customer.CustomerController;",
                        "createCustomer",
                        "timeout",
                        "repository"
                ).equals(query.keywords())
        ));

        assertEquals(
                List.of(
                        "entrypoint",
                        "service-or-orchestrator",
                        "repository",
                        "mapper",
                        "downstream-client"
                ),
                response.groups().stream().map(GitLabFlowContextGroup::role).toList()
        );
        assertEquals(1, response.groups().stream()
                .filter(group -> "repository".equals(group.role()))
                .findFirst()
                .orElseThrow()
                .candidates()
                .size());
        assertEquals(
                "crm-customer-api:src/main/java/com/example/crm/customer/CustomerProfileController.java (entrypoint, outline-then-focused-chunk)",
                response.recommendedNextReads().get(0)
        );
    }

    @Test
    void shouldFindClassReferencesUsingImportAndSimpleNameKeywords() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = gitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/domain/CustomerEntity.java",
                        "Matched exact import for CustomerEntity and @Entity hint.",
                        100
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/repository/CustomerProfileRepository.java",
                        "Repository using CustomerEntity in JpaRepository declaration.",
                        95
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/service/CustomerQueryService.java",
                        "Service imports CustomerEntity and loads aggregate state.",
                        90
                )
        ));

        var response = tools.findClassReferences(
                List.of("crm-customer-core"),
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                "com.example.crm.customer.domain.CustomerEntity",
                List.of("@Entity", "@Table", "mappedBy", "CustomerRepository"),
                List.of("GET /crm/customers/{customerId}"),
                2,
                "Szukam referencji klasy w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                query.correlationId() == null
                        && "CRM/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("crm-customer-core").equals(query.projectNames())
                        && List.of("GET /crm/customers/{customerId}").equals(query.operationNames())
                        && List.of(
                        "com.example.crm.customer.domain.CustomerEntity",
                        "CustomerEntity",
                        "import com.example.crm.customer.domain.CustomerEntity;",
                        "@Entity",
                        "@Table",
                        "mappedBy",
                        "CustomerRepository"
                ).equals(query.keywords())
        ));

        assertEquals("com.example.crm.customer.domain.CustomerEntity", response.searchedClass());
        assertEquals(
                List.of(
                        "service-or-orchestrator",
                        "repository",
                        "entity"
                ),
                response.groups().stream().map(GitLabFlowContextGroup::role).toList()
        );
        assertEquals(
                "crm-customer-core:src/main/java/com/example/crm/customer/domain/CustomerEntity.java (entity, outline-or-focused-chunk)",
                response.recommendedNextReads().get(0)
        );
    }

    @Test
    void shouldExposeHelpersForOutlineAndRoleClassification() {
        var tools = GitLabMcpToolsTestCreator.create(mock(GitLabRepositoryPort.class));

        var outline = tools.buildOutline("""
                package pl.mkn.config;

                import org.springframework.context.annotation.Bean;

                @Configuration
                public class CustomerProfileConfiguration {

                    @Bean
                    Scheduler customerProfileJobScheduler() {
                        return new Scheduler();
                    }
                }
                """);

        assertEquals("CustomerProfileConfiguration", tools.simpleName("pl.mkn.config.CustomerProfileConfiguration"));
        assertEquals("CustomerProfileConfiguration", tools.fileNameWithoutExtension("src/main/java/pl/mkn/config/CustomerProfileConfiguration.java"));
        assertIterableEquals(List.of("alpha", "beta"), tools.deduplicate(List.of(" alpha ", "beta", "alpha", "  ")));
        assertEquals("configuration", tools.inferRole("src/main/java/pl/mkn/config/CustomerProfileConfiguration.java", "@Configuration"));
        assertEquals("read-file-if-short", tools.recommendReadStrategy("src/main/resources/application.yml", "configuration"));
        assertEquals(1, tools.rolePriority("entrypoint"));
        assertIterableEquals(List.of("@Configuration"), outline.typeSummaries().get(0).annotations());
        assertTrue(outline.fieldSummaries().isEmpty());
        assertEquals(List.of("Scheduler customerProfileJobScheduler()"), outline.methodSummaries().stream()
                .map(GitLabJavaMethodSummary::signature)
                .toList());
        assertIterableEquals(List.of("@Bean"), outline.methodSummaries().get(0).annotations());
    }

    @Test
    void shouldExtractNeutralJavaFieldSummariesWithAnnotations() {
        var tools = GitLabMcpToolsTestCreator.create(mock(GitLabRepositoryPort.class));

        var outline = tools.buildOutline("""
                package pl.mkn.customer;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class CustomerEntity {
                    @Id
                    private Long id;

                    @Column(name = "customer_status")
                    private CustomerStatus status;

                    private final CustomerSource source = CustomerSource.REQUEST;
                }
                """);

        assertIterableEquals(
                List.of("id", "status", "source"),
                outline.fieldSummaries().stream().map(GitLabJavaFieldSummary::name).toList()
        );
        assertIterableEquals(List.of("@Entity"), outline.typeSummaries().get(0).annotations());
        var statusField = outline.fieldSummaries().get(1);
        assertTrue(statusField.declaringTypeName().endsWith("CustomerEntity"));
        assertEquals("CustomerStatus", statusField.type());
        assertIterableEquals(List.of("private"), statusField.modifiers());
        assertTrue(statusField.annotations().stream().anyMatch(annotation -> annotation.contains("@Column")));
        assertFalse(statusField.annotations().stream().anyMatch(annotation -> annotation.contains("inferredColumnName")));
        var sourceField = outline.fieldSummaries().get(2);
        assertIterableEquals(List.of("private", "final"), sourceField.modifiers());
    }

    @Test
    void shouldThrowReadableErrorWhenGitLabGroupCannotBeResolved() {
        var tools = GitLabMcpToolsTestCreator.create(mock(GitLabRepositoryPort.class));

        var exception = assertThrows(IllegalStateException.class, () -> tools.listAvailableRepositories(
                DEFAULT_APPLICATION_NAME,
                DEFAULT_BRANCH_REF,
                "Sprawdzam brak konfiguracji grupy.",
                gitLabToolContext()
        ));

        assertEquals(
                "GitLab group could not be resolved from operational context or analysis.gitlab.group.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowReadableErrorWhenBranchRefIsMissing() {
        var exception = assertThrows(IllegalArgumentException.class, () -> gitLabMcpTools.readRepositoryFile(
                "crm-customer-client-service",
                null,
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                120,
                "Czytam plik bez brancha.",
                gitLabToolContext()
        ));

        assertEquals(
                "branchRef must be provided explicitly for GitLab tools.",
                exception.getMessage()
        );
    }

    @Test
    void shouldUseResolvedScopeWithoutLegacyFeatureSpecificToolContext() {
        var response = gitLabMcpTools.readRepositoryFile(
                "crm-customer-client-service",
                DEFAULT_BRANCH_REF,
                DEFAULT_APPLICATION_NAME,
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                120,
                "Czytam plik bez legacy hidden scope.",
                gitLabToolContext()
        );

        assertEquals("CRM/backend", response.group());
        assertEquals("feature/INC-123", response.branch());
    }

    private ToolContext gitLabToolContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put(AgentToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(AgentToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(AgentToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(AgentToolContextKeys.TOOL_NAME, "gitlab_test_tool");
        return new ToolContext(context);
    }

    private GitLabMcpTools gitLabMcpTools(GitLabRepositoryPort gitLabRepositoryPort) {
        return GitLabMcpToolsTestCreator.create(gitLabRepositoryPort, gitLabProperties(DEFAULT_GROUP));
    }

    private static GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setGroup(group);
        return properties;
    }

    private OperationalContextCatalog operationalContextCatalog(Map<String, Object>... repositories) {
        return operationalContextCatalog(List.of(), repositories);
    }

    private OperationalContextCatalog operationalContextCatalog(
            List<Map<String, Object>> codeSearchScopes,
            Map<String, Object>... repositories
    ) {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(repositories),
                codeSearchScopes,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private Map<String, Object> codeSearchScope(
            String id,
            String targetType,
            String targetId,
            List<Map<String, Object>> repositories
    ) {
        var scope = new LinkedHashMap<String, Object>();
        scope.put("id", id);
        scope.put("name", "Backend component code search scope");
        scope.put("lifecycleStatus", "active");
        scope.put("target", Map.of(
                "type", targetType,
                "id", targetId
        ));
        scope.put("useFor", List.of("code-search", "incident-analysis"));
        scope.put("repositories", repositories);
        scope.put("limitations", List.of("Generated clients are partial in this fixture."));
        return scope;
    }

    private Map<String, Object> scopeRepository(
            String repoId,
            String role,
            int priority
    ) {
        return Map.of(
                "repoId", repoId,
                "role", role,
                "priority", priority,
                "reason", "Included in test code-search scope.",
                "readFor", List.of("code-navigation")
        );
    }

    private Map<String, Object> repository(
            String id,
            String name,
            String repositoryType,
            String lifecycleStatus,
            String group,
            String projectPath,
            String project,
            List<String> aliases,
            List<String> systems,
            List<String> boundedContexts,
            List<String> processes,
            List<String> integrations,
            List<String> relatedRepositories
    ) {
        var repository = new LinkedHashMap<String, Object>();
        repository.put("id", id);
        repository.put("name", name);
        repository.put("repositoryType", repositoryType);
        repository.put("lifecycleStatus", lifecycleStatus);
        repository.put("git", Map.of(
                "provider", "gitlab",
                "group", group,
                "projectPath", projectPath,
                "project", project,
                "aliases", aliases
        ));
        repository.put("purpose", "%s repository represented in operational context.".formatted(name));
        repository.put("references", Map.of(
                "systems", systems,
                "boundedContexts", boundedContexts,
                "processes", processes,
                "integrations", integrations,
                "repositories", relatedRepositories
        ));
        repository.put("matchSignals", Map.of(
                "strong", Map.of(
                        "projectNames", List.of(project),
                        "domainTerms", boundedContexts
                )
        ));
        return repository;
    }

}
