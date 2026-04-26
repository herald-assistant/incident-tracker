package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolAccessPolicyCoverageTest {

    @Test
    void shouldEnableGitLabToolsWhenNoDeterministicGitLabEvidenceIsAttached() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of()),
                tools("gitlab_search_repository_candidates", "gitlab_read_repository_file")
        );

        assertEquals(
                Set.of("gitlab_search_repository_candidates", "gitlab_read_repository_file"),
                Set.copyOf(policy.availableToolNames())
        );
    }

    @Test
    void shouldKeepFocusedGitLabToolsForFailingMethodOnlyEvidence() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(gitLabSection(
                        "CheckoutService.java around failing method",
                        """
                                class CheckoutService {
                                    Order submit(CheckoutCommand command) {
                                        if (command == null) {
                                            throw new IllegalArgumentException("command");
                                        }
                                        return command.toOrder();
                                    }
                                }
                                """
                ))),
                tools(
                        "gitlab_search_repository_candidates",
                        "gitlab_read_repository_file",
                        "gitlab_find_class_references",
                        "gitlab_find_flow_context",
                        "gitlab_read_repository_file_chunk",
                        "gitlab_read_repository_file_outline"
                )
        );

        assertFalse(policy.availableToolNames().contains("gitlab_search_repository_candidates"));
        assertFalse(policy.availableToolNames().contains("gitlab_read_repository_file"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_class_references"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_flow_context"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_chunk"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_outline"));
    }

    @Test
    void shouldDisableGitLabToolsWhenFlowContextIsAttached() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(gitLabSection(
                        "Flow context upstream and downstream for CheckoutService",
                        """
                                class CheckoutService {
                                    Order submit(CheckoutCommand command) {
                                        validator.validate(command);
                                        return downstreamClient.reserve(command.id());
                                    }
                                }
                                """
                ))),
                tools("gitlab_find_flow_context", "gitlab_read_repository_file_chunk")
        );

        assertEquals(List.of(), policy.availableToolNames());
        assertTrue(policy.disabledCapabilityGroups().stream()
                .anyMatch(group -> "gitlab".equals(group.get("name"))));
    }

    @Test
    void shouldDisableElasticToolsWhenLogsAreSufficient() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(sufficientElasticSection())),
                tools("elastic_search_logs_by_correlation_id")
        );

        assertEquals(List.of(), policy.availableToolNames());
    }

    @Test
    void shouldEnableElasticToolsWhenLogsAreTruncated() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(item(
                                "truncated log",
                                attr("serviceName", "billing-service"),
                                attr("className", "com.example.BillingService"),
                                attr("message", "Failed to settle invoice"),
                                attr("messageTruncated", "true")
                        ))
                ))),
                tools("elastic_search_logs_by_correlation_id")
        );

        assertEquals(List.of("elastic_search_logs_by_correlation_id"), policy.availableToolNames());
    }

    @Test
    void shouldEnableDatabaseToolsForRequiredDataDiagnosticWithResolvedEnvironmentExceptRawSql() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(jpaExceptionSection())),
                tools("db_get_scope", "db_find_tables", "db_exists_by_key", "db_execute_readonly_sql")
        );

        assertTrue(policy.availableToolNames().contains("db_get_scope"));
        assertTrue(policy.availableToolNames().contains("db_find_tables"));
        assertTrue(policy.availableToolNames().contains("db_exists_by_key"));
        assertFalse(policy.availableToolNames().contains("db_execute_readonly_sql"));
    }

    @Test
    void shouldDisableDatabaseToolsWhenDataNeedExistsButEnvironmentIsUnresolved() {
        var policy = CopilotToolAccessPolicy.from(
                request(null, List.of(jpaExceptionSection())),
                tools("db_get_scope", "db_find_tables")
        );

        assertEquals(List.of(), policy.availableToolNames());
        assertTrue(policy.evidenceCoverage().hasGap("DB_ENVIRONMENT_UNRESOLVED"));
    }

    @Test
    void shouldDisableDatabaseToolsWhenThereIsNoDataDiagnosticNeed() {
        var policy = CopilotToolAccessPolicy.from(
                request("dev3", List.of(sufficientElasticSection())),
                tools("db_get_scope", "db_find_tables")
        );

        assertEquals(List.of(), policy.availableToolNames());
    }

    private AnalysisAiAnalysisRequest request(String environment, List<AnalysisEvidenceSection> sections) {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                environment,
                "release/2026.04",
                "sample/runtime",
                sections
        );
    }

    private AnalysisEvidenceSection gitLabSection(String title, String content) {
        return new AnalysisEvidenceSection(
                "gitlab",
                "resolved-code",
                List.of(item(
                        title,
                        attr("projectName", "checkout-service"),
                        attr("filePath", "src/main/java/com/example/CheckoutService.java"),
                        attr("lineNumber", "12"),
                        attr("returnedStartLine", "8"),
                        attr("returnedEndLine", "18"),
                        attr("content", content),
                        attr("contentTruncated", "false")
                ))
        );
    }

    private AnalysisEvidenceSection sufficientElasticSection() {
        return new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(item(
                        "error log",
                        attr("serviceName", "billing-service"),
                        attr("className", "com.example.BillingService"),
                        attr("message", "Failed to settle invoice"),
                        attr("exception", """
                                java.lang.IllegalStateException: failed
                                \tat com.example.BillingService.settle(BillingService.java:42)
                                """)
                ))
        );
    }

    private AnalysisEvidenceSection jpaExceptionSection() {
        return new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(item(
                        "repository error",
                        attr("serviceName", "billing-service"),
                        attr("message", "EntityNotFoundException while loading tenant status by business key")
                ))
        );
    }

    private List<ToolDefinition> tools(String... names) {
        return List.of(names).stream().map(name -> ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        )).toList();
    }

    private AnalysisEvidenceItem item(String title, AnalysisEvidenceAttribute... attributes) {
        return new AnalysisEvidenceItem(title, List.of(attributes));
    }

    private AnalysisEvidenceAttribute attr(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }
}
