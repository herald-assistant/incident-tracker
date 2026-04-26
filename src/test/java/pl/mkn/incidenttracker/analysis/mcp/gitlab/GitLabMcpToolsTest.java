package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolContextKeys;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFileChunkRequest;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabToolScope;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GitLabMcpToolsTest {

    private final GitLabMcpTools gitLabMcpTools = new GitLabMcpTools(new TestGitLabRepositoryPort());

    @Test
    void shouldDelegateSearchRepositoryCandidatesUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                "Matched timeout-related service and log keywords.",
                95
        )));

        var response = tools.searchRepositoryCandidates(
                List.of("billing-service", "catalog-service"),
                List.of("GET /inventory"),
                List.of("timeout", "inventory"),
                "Sprawdzam kandydatow repozytorium dla testu.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
                        && "platform/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("billing-service", "catalog-service").equals(query.projectNames())
                        && List.of("GET /inventory").equals(query.operationNames())
                        && List.of("timeout", "inventory").equals(query.keywords())
        ));
        assertEquals(1, response.candidates().size());
        assertEquals("edge-client-service", response.candidates().get(0).projectName());
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
                List.of("document-workflow"),
                List.of(),
                List.of("document"),
                "Sprawdzam zagniezdzony projekt.",
                gitLabToolContext("TENANT-ALPHA", "release-candidate", "agreement-123")
        );

        assertEquals(1, response.candidates().size());
        assertEquals("TENANT-ALPHA", response.candidates().get(0).group());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", response.candidates().get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/DocumentWorkflowService.java", response.candidates().get(0).filePath());
    }

    @Test
    void shouldDelegateReadRepositoryFileUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        )).thenReturn(new GitLabRepositoryFileContent(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                "class CatalogGatewayClient {}",
                false
        ));

        var response = tools.readRepositoryFile(
                "edge-client-service",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120,
                "Czytam plik w tescie delegacji.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        );
        assertEquals("platform/backend", response.group());
        assertEquals("edge-client-service", response.projectName());
        assertEquals("feature/INC-123", response.branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", response.filePath());
        assertEquals("class CatalogGatewayClient {}", response.content());
        assertFalse(response.truncated());
    }

    @Test
    void shouldUseDefaultCharacterLimitWhenNotProvided() {
        var response = gitLabMcpTools.readRepositoryFile(
                "ledger-write-service",
                "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java",
                null,
                "Czytam plik z domyslnym limitem.",
                gitLabToolContext("sample/runtime", "release/2026.04", "corr-123")
        );

        assertFalse(response.truncated());
        assertTrue(response.content().contains("LedgerTransactionService"));
        assertEquals("sample/runtime", response.group());
        assertEquals("release/2026.04", response.branch());
    }

    @Test
    void shouldDelegateReadRepositoryFileChunkUsingSessionBoundScope() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFileChunk(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                5,
                12,
                14,
                "return catalogWebClient.get();",
                false
        ));

        var response = tools.readRepositoryFileChunk(
                "edge-client-service",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000,
                "Czytam fragment pliku w tescie delegacji.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "platform/backend",
                "edge-client-service",
                "feature/INC-123",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        );
        assertEquals(5, response.requestedStartLine());
        assertEquals(12, response.requestedEndLine());
        assertEquals(5, response.returnedStartLine());
        assertEquals(12, response.returnedEndLine());
        assertEquals(14, response.totalLines());
        assertEquals("return catalogWebClient.get();", response.content());
        assertFalse(response.truncated());
    }

    @Test
    void shouldReadRepositoryFileOutlineAndInferRole() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                30_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                """
                        package pl.mkn.billing;

                        import java.util.List;
                        import org.springframework.stereotype.Service;

                        @Service
                        public class BillingService {

                            public OrderResult processOrder(String orderId) {
                                return OrderResult.success(orderId);
                            }

                            private void validate(Order order) {
                            }
                        }
                        """,
                false
        ));

        var response = tools.readRepositoryFileOutline(
                "billing-service",
                "src/main/java/pl/mkn/billing/BillingService.java",
                null,
                "Sprawdzam zarys pliku w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFile(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                30_000
        );
        assertEquals("pl.mkn.billing", response.packageName());
        assertIterableEquals(
                List.of(
                        "import java.util.List;",
                        "import org.springframework.stereotype.Service;"
                ),
                response.imports()
        );
        assertIterableEquals(List.of("public class BillingService {"), response.classes());
        assertIterableEquals(List.of("@Service"), response.annotations());
        assertIterableEquals(
                List.of(
                        "public OrderResult processOrder(String orderId)",
                        "private void validate(Order order)"
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
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                10,
                20,
                10
        )).thenReturn(new GitLabRepositoryFileChunk(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                10,
                20,
                10,
                20,
                120,
                "1234567890",
                false
        ));
        when(gitLabRepositoryPort.readFileChunk(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/OrderRepository.java",
                30,
                40,
                5
        )).thenReturn(new GitLabRepositoryFileChunk(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/OrderRepository.java",
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
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/BillingService.java", 10, 20, 10),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OrderRepository.java", 30, 40, 10),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OrderMapper.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OrderValidator.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/PaymentClient.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OrderController.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OutboxPublisher.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/OrderJob.java", 1, 5, 100),
                        new GitLabFileChunkRequest("billing-service", "src/main/java/pl/mkn/billing/Extra.java", 1, 5, 100)
                ),
                15,
                "Czytam kilka fragmentow w tescie limitow.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/BillingService.java",
                10,
                20,
                10
        );
        verify(gitLabRepositoryPort).readFileChunk(
                "platform/backend",
                "billing-service",
                "feature/INC-123",
                "src/main/java/pl/mkn/billing/OrderRepository.java",
                30,
                40,
                5
        );
        verifyNoMoreInteractions(gitLabRepositoryPort);

        assertEquals(2, response.chunks().size());
        assertEquals("platform/backend", response.group());
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
                        "platform/backend",
                        "orders-api",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/OrderController.java",
                        "Controller handling submitOrder endpoint.",
                        100
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/OrderService.java",
                        "Service orchestrates submitOrder flow.",
                        95
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/OrderRepository.java",
                        "Repository predicate used in submitOrder.",
                        92
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/LegacyOrderRepository.java",
                        "Fallback repository path.",
                        80
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/OrderMapper.java",
                        "Mapper between API and domain model.",
                        75
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-client",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/PaymentClient.java",
                        "HTTP client calling payment system.",
                        70
                )
        ));

        var response = tools.findFlowContext(
                List.of("orders-api", "orders-core"),
                List.of(
                        "pl.mkn.orders.OrderController",
                        "OrderController",
                        "import pl.mkn.orders.OrderController;",
                        "submitOrder",
                        "timeout",
                        "repository"
                ),
                List.of("POST /orders"),
                1,
                "Szukam kontekstu przeplywu w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
                        && "platform/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("orders-api", "orders-core").equals(query.projectNames())
                        && List.of("POST /orders").equals(query.operationNames())
                        && List.of(
                        "pl.mkn.orders.OrderController",
                        "OrderController",
                        "import pl.mkn.orders.OrderController;",
                        "submitOrder",
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
                "orders-api:src/main/java/pl/mkn/orders/OrderController.java (entrypoint, outline-then-focused-chunk)",
                response.recommendedNextReads().get(0)
        );
    }

    @Test
    void shouldFindClassReferencesUsingImportAndSimpleNameKeywords() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/domain/OrderEntity.java",
                        "Matched exact import for OrderEntity and @Entity hint.",
                        100
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/repository/OrderRepository.java",
                        "Repository using OrderEntity in JpaRepository declaration.",
                        95
                ),
                new GitLabRepositoryFileCandidate(
                        "platform/backend",
                        "orders-core",
                        "feature/INC-123",
                        "src/main/java/pl/mkn/orders/service/OrderQueryService.java",
                        "Service imports OrderEntity and loads aggregate state.",
                        90
                )
        ));

        var response = tools.findClassReferences(
                List.of("orders-core"),
                "pl.mkn.orders.domain.OrderEntity",
                List.of("@Entity", "@Table", "mappedBy", "OrderRepository"),
                List.of("GET /orders/{id}"),
                2,
                "Szukam referencji klasy w tescie.",
                gitLabToolContext()
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
                        && "platform/backend".equals(query.group())
                        && "feature/INC-123".equals(query.branch())
                        && List.of("orders-core").equals(query.projectNames())
                        && List.of("GET /orders/{id}").equals(query.operationNames())
                        && List.of(
                        "pl.mkn.orders.domain.OrderEntity",
                        "OrderEntity",
                        "import pl.mkn.orders.domain.OrderEntity;",
                        "@Entity",
                        "@Table",
                        "mappedBy",
                        "OrderRepository"
                ).equals(query.keywords())
        ));

        assertEquals("pl.mkn.orders.domain.OrderEntity", response.searchedClass());
        assertEquals(
                List.of(
                        "service-or-orchestrator",
                        "repository",
                        "entity"
                ),
                response.groups().stream().map(GitLabFlowContextGroup::role).toList()
        );
        assertEquals(
                "orders-core:src/main/java/pl/mkn/orders/domain/OrderEntity.java (entity, outline-or-focused-chunk)",
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
                CopilotToolContextKeys.GITLAB_GROUP
        )));

        assertEquals(
                "Missing gitLabGroup in Copilot tool context; GitLab tools require session-bound group.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowReadableErrorWhenGitLabBranchIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> GitLabToolScope.from(toolContextWithout(
                CopilotToolContextKeys.GITLAB_BRANCH
        )));

        assertEquals(
                "Missing gitLabBranch in Copilot tool context; GitLab tools require resolved session branch.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowReadableErrorWhenCorrelationIdIsMissing() {
        var exception = assertThrows(IllegalStateException.class, () -> GitLabToolScope.from(toolContextWithout(
                CopilotToolContextKeys.CORRELATION_ID
        )));

        assertEquals(
                "Missing correlationId in Copilot tool context; GitLab tools require session-bound correlationId.",
                exception.getMessage()
        );
    }

    private ToolContext gitLabToolContext() {
        return gitLabToolContext("platform/backend", "feature/INC-123", "corr-123");
    }

    private ToolContext gitLabToolContext(String group, String branch, String correlationId) {
        var context = new LinkedHashMap<String, Object>();
        context.put(CopilotToolContextKeys.CORRELATION_ID, correlationId);
        context.put(CopilotToolContextKeys.GITLAB_GROUP, group);
        context.put(CopilotToolContextKeys.GITLAB_BRANCH, branch);
        context.put(CopilotToolContextKeys.ENVIRONMENT, "zt01");
        context.put(CopilotToolContextKeys.ANALYSIS_RUN_ID, "run-1");
        context.put(CopilotToolContextKeys.COPILOT_SESSION_ID, "analysis-run-1");
        context.put(CopilotToolContextKeys.TOOL_CALL_ID, "tool-call-1");
        context.put(CopilotToolContextKeys.TOOL_NAME, "gitlab_test_tool");
        return new ToolContext(context);
    }

    private ToolContext toolContextWithout(String keyToRemove) {
        var context = new LinkedHashMap<>(gitLabToolContext().getContext());
        context.remove(keyToRemove);
        return new ToolContext(context);
    }
}
