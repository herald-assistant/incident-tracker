package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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
    void shouldDelegateSearchRepositoryCandidatesWithCompatibleQueryShape() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                "Matched timeout-related service and log keywords.",
                95
        )));

        var response = tools.searchRepositoryCandidates(
                "timeout-123",
                "sample/runtime",
                "release/2026.04",
                List.of("billing-service", "catalog-service"),
                List.of("GET /inventory"),
                List.of("timeout", "inventory")
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "timeout-123".equals(query.correlationId())
                        && "sample/runtime".equals(query.group())
                        && "release/2026.04".equals(query.branch())
                        && List.of("billing-service", "catalog-service").equals(query.projectNames())
                        && List.of("GET /inventory").equals(query.operationNames())
                        && List.of("timeout", "inventory").equals(query.keywords())
        ));
        assertEquals(1, response.candidates().size());
        assertEquals("edge-client-service", response.candidates().get(0).projectName());
    }

    @Test
    void shouldSearchRepositoryCandidatesThroughToolUsingComponentHintForNestedProject() {
        var response = gitLabMcpTools.searchRepositoryCandidates(
                "agreement-123",
                "TENANT-ALPHA",
                "release-candidate",
                List.of("document-workflow"),
                List.of(),
                List.of("document")
        );

        assertEquals(1, response.candidates().size());
        assertEquals("TENANT-ALPHA", response.candidates().get(0).group());
        assertEquals("WORKFLOWS/DOCUMENT_WORKFLOW", response.candidates().get(0).projectName());
        assertEquals("src/main/java/com/example/synthetic/workflow/DocumentWorkflowService.java", response.candidates().get(0).filePath());
    }

    @Test
    void shouldDelegateReadRepositoryFileWithExistingContract() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFile(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        )).thenReturn(new GitLabRepositoryFileContent(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                "class CatalogGatewayClient {}",
                false
        ));

        var response = tools.readRepositoryFile(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        );

        verify(gitLabRepositoryPort).readFile(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                120
        );
        assertEquals("sample/runtime", response.group());
        assertEquals("edge-client-service", response.projectName());
        assertEquals("release/2026.04", response.branch());
        assertEquals("src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java", response.filePath());
        assertEquals("class CatalogGatewayClient {}", response.content());
        assertFalse(response.truncated());
    }

    @Test
    void shouldUseDefaultCharacterLimitWhenNotProvided() {
        var response = gitLabMcpTools.readRepositoryFile(
                "sample/runtime",
                "ledger-write-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java",
                null
        );

        assertFalse(response.truncated());
        assertTrue(response.content().contains("LedgerTransactionService"));
    }

    @Test
    void shouldDelegateReadRepositoryFileChunkWithExistingContract() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.readFileChunk(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
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
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
                "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                5,
                12,
                4_000
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "sample/runtime",
                "edge-client-service",
                "release/2026.04",
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
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/BillingService.java",
                30_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
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
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/BillingService.java",
                null
        );

        verify(gitLabRepositoryPort).readFile(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
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
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/BillingService.java",
                10,
                20,
                10
        )).thenReturn(new GitLabRepositoryFileChunk(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
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
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/OrderRepository.java",
                30,
                40,
                5
        )).thenReturn(new GitLabRepositoryFileChunk(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
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
                "sample/runtime",
                "release/2026.04",
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
                15
        );

        verify(gitLabRepositoryPort).readFileChunk(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/BillingService.java",
                10,
                20,
                10
        );
        verify(gitLabRepositoryPort).readFileChunk(
                "sample/runtime",
                "billing-service",
                "release/2026.04",
                "src/main/java/pl/mkn/billing/OrderRepository.java",
                30,
                40,
                5
        );
        verifyNoMoreInteractions(gitLabRepositoryPort);

        assertEquals(2, response.chunks().size());
        assertTrue(response.chunkCountTruncated());
        assertTrue(response.totalCharacterLimitReached());
        assertEquals("service-or-orchestrator", response.chunks().get(0).inferredRole());
        assertEquals("repository", response.chunks().get(1).inferredRole());
    }

    @Test
    void shouldFindFlowContextGroupedByRoleAndUseSeedKeywords() {
        var gitLabRepositoryPort = mock(GitLabRepositoryPort.class);
        var tools = new GitLabMcpTools(gitLabRepositoryPort);
        when(gitLabRepositoryPort.searchCandidateFiles(any())).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-api",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/OrderController.java",
                        "Controller handling submitOrder endpoint.",
                        100
                ),
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-core",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/OrderService.java",
                        "Service orchestrates submitOrder flow.",
                        95
                ),
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-core",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/OrderRepository.java",
                        "Repository predicate used in submitOrder.",
                        92
                ),
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-core",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/LegacyOrderRepository.java",
                        "Fallback repository path.",
                        80
                ),
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-core",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/OrderMapper.java",
                        "Mapper between API and domain model.",
                        75
                ),
                new GitLabRepositoryFileCandidate(
                        "sample/runtime",
                        "orders-client",
                        "release/2026.04",
                        "src/main/java/pl/mkn/orders/PaymentClient.java",
                        "HTTP client calling payment system.",
                        70
                )
        ));

        var response = tools.findFlowContext(
                "corr-123",
                "sample/runtime",
                "release/2026.04",
                List.of("orders-api", "orders-core"),
                "pl.mkn.orders.OrderController",
                "submitOrder",
                "src/main/java/pl/mkn/orders/OrderController.java",
                List.of("timeout", "repository"),
                List.of("POST /orders"),
                1
        );

        verify(gitLabRepositoryPort).searchCandidateFiles(argThat(query ->
                "corr-123".equals(query.correlationId())
                        && "sample/runtime".equals(query.group())
                        && "release/2026.04".equals(query.branch())
                        && List.of("orders-api", "orders-core").equals(query.projectNames())
                        && List.of("POST /orders").equals(query.operationNames())
                        && List.of(
                        "pl.mkn.orders.OrderController",
                        "OrderController",
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
    void shouldExposeHelperHeuristicsForOutlineAndRoleClassification() {
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
}
