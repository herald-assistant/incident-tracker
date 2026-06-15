package pl.mkn.incidenttracker.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileRole;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelationKind;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRepositoryContext;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFileChunkRequest;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabToolScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final GitLabMcpTools gitLabMcpTools = new GitLabMcpTools(new TestGitLabRepositoryPort());

    @Test
    void shouldListAvailableRepositoriesFromOperationalContextUsingSessionGroup() {
        var tools = new GitLabMcpTools(
                mock(GitLabRepositoryPort.class),
                ignored -> operationalContextCatalog(
                        List.of(codeSearchScope(
                                "backend-component-code-search",
                                "system",
                                "backend",
                                List.of(
                                        scopeRepository("backend-repo", "primary-implementation", 1, List.of("backend-module")),
                                        scopeRepository("customer-repo", "workflow-collaborator", 2, List.of("customer-bootstrap"))
                                ),
                                List.of("com.example.crm.backend"),
                                List.of("DecisionController")
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
                                List.of("shared-lib-repo"),
                                List.of("com.example.crm.backend"),
                                List.of("/crm/process/decision"),
                                List.of("backend-module")
                        ),
                        repository(
                                "customer-repo",
                                "Customer",
                                "service",
                                "active",
                                "CRM",
                                "CRM/PROCESSES/CRM_CUSTOMER_PROCESS",
                                "CRM_CUSTOMER_PROCESS",
                                List.of("customer-process"),
                                List.of("customer"),
                                List.of("customer"),
                                List.of("customer-process"),
                                List.of(),
                                List.of(),
                                List.of("com.example.crm.customer"),
                                List.of("/crm/customer"),
                                List.of("customer-bootstrap")
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
                                List.of(),
                                List.of("com.example.outside"),
                                List.of("/outside"),
                                List.of("outside-module")
                        )
                )
        );

        var response = tools.listAvailableRepositories(
                "Sprawdzam katalog repozytoriow.",
                gitLabToolContext("CRM", "release/2026.04", "corr-123")
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
        assertIterableEquals(List.of("com.example.crm.backend"), backend.packagePrefixes());
        assertIterableEquals(List.of("/crm/process/decision"), backend.endpointPrefixes());
        assertIterableEquals(List.of("backend-module"), backend.modulePaths());

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
        assertEquals("primary-implementation", scope.repositories().get(0).role());
        assertEquals("workflow-collaborator", scope.repositories().get(1).role());
        assertIterableEquals(List.of("backend-module"), scope.repositories().get(0).moduleIds());
        assertTrue(scope.traversal().expandWhen().contains("Expand when supporting repositories are needed."));
    }

    @Test
    void shouldDelegateSearchRepositoryCandidatesUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM/backend",
                "crm-customer-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                "Matched timeout-related service and log keywords.",
                95
        )));

        var response = tools.searchRepositoryCandidates(
                List.of("crm-billing-service", "crm-customer-profile-service"),
                List.of("GET /crm/customers"),
                List.of("timeout", "customer-profile"),
                "Sprawdzam kandydatow repozytorium dla testu.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
                        && "CRM/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("crm-billing-service", "crm-customer-profile-service").equals(query.projectNames())
                        && List.of("GET /crm/customers").equals(query.operationNames())
                        && List.of("timeout", "customer-profile").equals(query.keywords())
        ));
        assertEquals(1, response.candidates().size());
        assertEquals("crm-customer-client-service", response.candidates().get(0).projectName());
    }

    @Test
    void shouldDefaultNullListsToEmptyListsInSearchRepositoryCandidates() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of());

        tools.searchRepositoryCandidates(null, null, null, "Sprawdzam puste wejscie.", gitLabToolContext());

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                query.projectNames().isEmpty()
                        && query.operationNames().isEmpty()
                        && query.keywords().isEmpty()
        ));
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughToolUsingComponentHintForNestedProject() {
        var response = gitLabMcpTools.searchRepositoryCandidates(
                List.of("crm-customer-workflow"),
                List.of(),
                List.of("customer-profile"),
                "Sprawdzam zagniezdzony projekt.",
                gitLabToolContext("CRM", "release-candidate", "customer-123")
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
                "/api/customers",
                "GET",
                50,
                "Listuje endpointy zamowien.",
                gitLabToolContext("CRM/backend", "feature/FLOW-1", "flow-123")
        );

        assertEquals("CRM/backend", response.group());
        assertEquals("crm-customer-api", response.projectName());
        assertEquals("feature/FLOW-1", response.branch());
        assertEquals("/api/customers", response.endpointPathPrefix());
        assertEquals("GET", response.httpMethod());
        assertEquals(2, response.candidateFileCount());
        assertEquals(2, response.scannedFileCount());
        assertFalse(response.scannedFileLimitReached());
        assertEquals(1, response.endpoints().size());

        var endpoint = response.endpoints().get(0);
        assertEquals("/api/customers/{customerId}", endpoint.path());
        assertIterableEquals(List.of("GET"), endpoint.httpMethods());
        assertEquals("com.example.crm.customer.api.CustomerController", endpoint.controllerClass());
        assertEquals("getCustomer", endpoint.handlerMethod());
        assertEquals("src/main/java/com/example/crm/customer/api/CustomerController.java", endpoint.filePath());
        assertIterableEquals(List.of("@PathVariable String customerId"), endpoint.requestTypes());
        assertIterableEquals(List.of("ResponseEntity<OrderResponse>"), endpoint.responseTypes());
        assertTrue(endpoint.annotations().contains("RestController"));
        assertTrue(endpoint.annotations().contains("GetMapping"));
        assertEquals("high", endpoint.confidence());
        assertTrue(endpoint.limitations().isEmpty());
        assertTrue(endpoint.suggestedNextReads().get(0).contains("gitlab_read_repository_file_chunk"));
    }

    @Test
    void shouldBuildEndpointUseCaseContextUsingSessionBoundScope() {
        var endpointUseCaseContextService = mock(GitLabEndpointUseCaseContextService.class);
        var tools = new GitLabMcpTools(
                mock(GitLabRepositoryPort.class),
                ignored -> OperationalContextCatalog.empty(),
                mock(GitLabRepositoryEndpointService.class),
                endpointUseCaseContextService
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
                                "src/main/java/com/example/crm/customer/CustomerController.java",
                                10,
                                18,
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

        var response = tools.buildEndpointUseCaseContext(
                "crm-customer-api",
                "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                null,
                null,
                4,
                12,
                "Buduje liste plikow dla endpointu zamowienia.",
                gitLabToolContext("CRM/backend", "feature/FLOW-1", "flow-123")
        );

        verify(endpointUseCaseContextService).buildContext(
                eq("CRM/backend"),
                eq("feature/FLOW-1"),
                argThat(request -> "crm-customer-api".equals(request.projectName())
                        && "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer".equals(request.endpointId())
                        && request.httpMethod() == null
                        && request.endpointPath() == null
                        && request.maxDepth() == 4
                        && request.maxFiles() == 12
                        && "Buduje liste plikow dla endpointu zamowienia.".equals(request.reason()))
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
    void shouldDelegateReadRepositoryFileUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
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
    void shouldUseDefaultCharacterLimitWhenNotProvided() {
        var response = gitLabMcpTools.readRepositoryFile(
                "crm-customer-account-service",
                "src/main/java/com/example/crm/customer/account/CustomerAccountService.java",
                null,
                "Czytam plik z domyslnym limitem.",
                gitLabToolContext("CRM/runtime", "release/2026.04", "corr-123")
        );

        assertFalse(response.truncated());
        assertTrue(response.content().contains("CustomerAccountService"));
        assertEquals("CRM/runtime", response.group());
        assertEquals("release/2026.04", response.branch());
    }

    @Test
    void shouldReadRepositoryFilesByPathUsingSessionScopeAndRespectLimits() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
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
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                5
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                "abcde",
                true
        ));
        when(gitLabRepositoryPort.readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerRepository.java"
        )).thenReturn(new GitLabRepositoryFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                "blob-repository",
                "commit-repository",
                "last-repository",
                "2026-06-16T09:00:00.000Z",
                "sha-repository",
                4321L
        ));

        var response = tools.readRepositoryFilesByPath(
                "crm-customer-api",
                List.of(
                        "/src/main/java/com/example/crm/customer/MissingCustomerService.java",
                        "crm-customer-api:src/main/java/com/example/crm/customer/CustomerService.java via gitlab_read_repository_file_outline",
                        "\\src\\main\\java\\com\\example\\crm\\customer\\CustomerRepository.java",
                        "src/main/java/com/example/crm/customer/CustomerMapper.java"
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
                "src/main/java/com/example/crm/customer/CustomerRepository.java",
                5
        );
        verify(gitLabRepositoryPort).readFileMetadata(
                "CRM/backend",
                "crm-customer-api",
                "feature/INC-123",
                "src/main/java/com/example/crm/customer/CustomerRepository.java"
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
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
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
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                30_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                """
                        package com.example.crm.billing;

                        import java.util.List;
                        import org.springframework.stereotype.Service;

                        @Service
                        public class CustomerBillingService {

                            public OrderResult processOrder(String customerId) {
                                return OrderResult.success(customerId);
                            }

                            private void validate(Customer customer) {
                            }
                        }
                        """,
                false
        ));

        var response = tools.readRepositoryFileOutline(
                "crm-billing-service",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                null,
                "Sprawdzam zarys pliku w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                30_000
        );
        assertEquals("com.example.crm.billing", response.packageName());
        assertIterableEquals(
                List.of(
                        "import java.util.List;",
                        "import org.springframework.stereotype.Service;"
                ),
                response.imports()
        );
        assertIterableEquals(List.of("public class CustomerBillingService {"), response.classes());
        assertIterableEquals(List.of("@Service"), response.annotations());
        assertIterableEquals(
                List.of(
                        "public OrderResult processOrder(String customerId)",
                        "private void validate(Customer customer)"
                ),
                response.methodSignatures()
        );
        assertEquals("service-or-orchestrator", response.inferredRole());
        assertFalse(response.truncated());
    }

    @Test
    void shouldReadRepositoryFileChunksAndRespectBatchAndCharacterLimits() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFileChunk(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                10,
                20,
                10
        )).thenReturn(new GitLabRepositoryFileChunk(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
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
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerRepository.java",
                30,
                40,
                5
        )).thenReturn(new GitLabRepositoryFileChunk(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerRepository.java",
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
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerBillingService.java", 10, 20, 10),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerRepository.java", 30, 40, 10),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerMapper.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerValidator.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/PaymentClient.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerController.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/OutboxPublisher.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/CustomerJob.java", 1, 5, 100),
                        new GitLabFileChunkRequest("crm-billing-service", "src/main/java/com/example/crm/billing/Extra.java", 1, 5, 100)
                ),
                15,
                "Czytam kilka fragmentow w tescie limitow.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerBillingService.java",
                10,
                20,
                10
        );
        verify(gitLabRepositoryPort).readFileChunk(
                "CRM/backend",
                "crm-billing-service",
                "feature/INC-123",
                "src/main/java/com/example/crm/billing/CustomerRepository.java",
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
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-api",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerController.java",
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
                        "src/main/java/com/example/crm/customer/CustomerRepository.java",
                        "Repository predicate used in submitOrder.",
                        92
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/LegacyCustomerRepository.java",
                        "Fallback repository path.",
                        80
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-core",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/CustomerMapper.java",
                        "Mapper between API and domain model.",
                        75
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM/backend",
                        "crm-customer-client",
                        "feature/INC-123",
                        "src/main/java/com/example/crm/customer/PaymentClient.java",
                        "HTTP client calling payment system.",
                        70
                )
        ));

        var response = tools.findFlowContext(
                List.of("crm-customer-api", "crm-customer-core"),
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
                "corr-123".equals(query.correlationId())
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
                "crm-customer-api:src/main/java/com/example/crm/customer/CustomerController.java (entrypoint, outline-then-focused-chunk)",
                response.recommendedNextReads().get(0)
        );
    }

    @Test
    void shouldFindClassReferencesUsingImportAndSimpleNameKeywords() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
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
                        "src/main/java/com/example/crm/customer/repository/CustomerRepository.java",
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
                "com.example.crm.customer.domain.CustomerEntity",
                List.of("@Entity", "@Table", "mappedBy", "CustomerRepository"),
                List.of("GET /crm/customers/{customerId}"),
                2,
                "Szukam referencji klasy w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
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
        var tools = new GitLabMcpTools(mock(GitLabRepositoryPort.class));

        var outline = tools.buildOutline("""
                package pl.mkn.config;

                import org.springframework.context.annotation.Bean;

                @Configuration
                public class BillingConfiguration {

                    @Bean
                    Scheduler billingJobScheduler() {
                        return new Scheduler();
                    }
                }
                """);

        assertEquals("BillingConfiguration", tools.simpleName("pl.mkn.config.BillingConfiguration"));
        assertEquals("BillingConfiguration", tools.fileNameWithoutExtension("src/main/java/pl/mkn/config/BillingConfiguration.java"));
        assertIterableEquals(List.of("alpha", "beta"), tools.deduplicate(List.of(" alpha ", "beta", "alpha", "  ")));
        assertEquals("configuration", tools.inferRole("src/main/java/pl/mkn/config/BillingConfiguration.java", "@Configuration"));
        assertEquals("read-file-if-short", tools.recommendReadStrategy("src/main/resources/application.yml", "configuration"));
        assertEquals(1, tools.rolePriority("entrypoint"));
        assertEquals(List.of("@Configuration", "@Bean"), outline.annotations());
        assertEquals(List.of("Scheduler billingJobScheduler()"), outline.methodSignatures());
    }

    @Test
    void shouldThrowReadableErrorWhenGitLabGroupIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> GitLabToolScope.from(toolContextWithout(
                AgentToolContextKeys.GITLAB_GROUP
        )));

        assertEquals(
                "Missing gitLabGroup in Copilot tool context; GitLab tools require session-bound group.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowReadableErrorWhenGitLabBranchIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> GitLabToolScope.from(toolContextWithout(
                AgentToolContextKeys.GITLAB_BRANCH
        )));

        assertEquals(
                "Missing gitLabBranch in Copilot tool context; GitLab tools require resolved session branch.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowReadableErrorWhenCorrelationIdIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> GitLabToolScope.from(toolContextWithout(
                AgentToolContextKeys.CORRELATION_ID
        )));

        assertEquals(
                "Missing correlationId in Copilot tool context; GitLab tools require session-bound correlationId.",
                exception.getMessage()
        );
    }

    private ToolContext gitLabToolContext() {
        return gitLabToolContext("CRM/backend", "feature/INC-123", "corr-123");
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
            List<Map<String, Object>> repositories,
            List<String> packagePrefixes,
            List<String> classHints
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
        scope.put("hints", Map.of(
                "packagePrefixes", packagePrefixes,
                "classHints", classHints
        ));
        scope.put("traversal", Map.of(
                "rules", List.of("Read repositories in priority customer."),
                "expandWhen", List.of("Expand when supporting repositories are needed.")
        ));
        return scope;
    }

    private Map<String, Object> scopeRepository(
            String repoId,
            String role,
            int priority,
            List<String> moduleIds
    ) {
        return Map.of(
                "repoId", repoId,
                "role", role,
                "priority", priority,
                "moduleIds", moduleIds,
                "reason", "Included in test code-search scope."
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
            List<String> relatedRepositories,
            List<String> packagePrefixes,
            List<String> endpointPrefixes,
            List<String> modulePaths
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
        repository.put("sourceLayout", Map.of(
                "modulePaths", modulePaths
        ));
        repository.put("matchSignals", Map.of(
                "strong", Map.of(
                        "packagePrefixes", packagePrefixes,
                        "endpointPrefixes", endpointPrefixes
                )
        ));
        return repository;
    }

    private ToolContext gitLabToolContext(String group, String branch, String correlationId) {
        var context = new LinkedHashMap<String, Object>();
        context.put(AgentToolContextKeys.CORRELATION_ID, correlationId);
        context.put(AgentToolContextKeys.GITLAB_GROUP, group);
        context.put(AgentToolContextKeys.GITLAB_BRANCH, branch);
        context.put(AgentToolContextKeys.ENVIRONMENT, "zt01");
        context.put(AgentToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(AgentToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(AgentToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(AgentToolContextKeys.TOOL_NAME, "gitlab_test_tool");
        return new ToolContext(context);
    }

    private ToolContext toolContextWithout(String keyToRemove) {
        var context = new LinkedHashMap<>(gitLabToolContext().getContext());
        context.remove(keyToRemove);
        return new ToolContext(context);
    }
}
