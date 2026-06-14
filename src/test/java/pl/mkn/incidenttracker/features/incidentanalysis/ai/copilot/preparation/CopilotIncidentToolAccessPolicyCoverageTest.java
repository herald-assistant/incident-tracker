package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentToolAccessPolicyCoverageTest {

    private final CopilotIncidentToolAccessPolicyFactory policyFactory =
            new CopilotIncidentToolAccessPolicyFactory(new CopilotIncidentEvidenceCoverageEvaluator());

    @Test
    void shouldEnableGitLabToolsWhenNoDeterministicGitLabEvidenceIsAttached() {
        var policy = policy(
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
        var policy = policy(
                request("dev3", List.of(gitLabSection(
                        "CheckoutService.java around failing method",
                        """
                                class CheckoutService {
                                    Customer submit(CheckoutCommand command) {
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
                        "gitlab_list_available_repositories",
                        "gitlab_find_class_references",
                        "gitlab_find_flow_context",
                        "gitlab_read_repository_file_chunk",
                        "gitlab_read_repository_file_outline"
                )
        );

        assertFalse(policy.availableToolNames().contains("gitlab_search_repository_candidates"));
        assertFalse(policy.availableToolNames().contains("gitlab_read_repository_file"));
        assertTrue(policy.availableToolNames().contains("gitlab_list_available_repositories"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_class_references"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_flow_context"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_chunk"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_outline"));
    }

    @Test
    void shouldKeepFocusedGitLabToolsWhenFlowContextIsAttachedForTechnicalAnalysisGrounding() {
        var policy = policy(
                request("dev3", List.of(gitLabSection(
                        "Flow context upstream and downstream for CheckoutService",
                        """
                                class CheckoutService {
                                    Customer submit(CheckoutCommand command) {
                                        validator.validate(command);
                                        return downstreamClient.reserve(command.id());
                                    }
                                }
                                """
                ))),
                tools("gitlab_find_flow_context", "gitlab_read_repository_file_chunk")
        );

        assertTrue(policy.evidenceCoverage().hasGap("TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED"));
        assertEquals(
                Set.of("gitlab_find_flow_context", "gitlab_read_repository_file_chunk"),
                Set.copyOf(policy.availableToolNames())
        );
        assertTrue(policy.disabledCapabilityGroups().stream()
                .noneMatch(group -> "gitlab".equals(group.get("name"))));
    }

    @Test
    void shouldEnableFocusedGitLabToolsForDatabaseCodeGroundingGapEvenWhenFlowContextIsAttached() {
        var policy = policy(
                request("dev3", List.of(
                        jpaExceptionSection(),
                        gitLabSection(
                                "Flow context upstream and downstream for CheckoutService",
                                """
                                        class CheckoutService {
                                            Customer submit(CheckoutCommand command) {
                                                validator.validate(command);
                                                return downstreamClient.reserve(command.id());
                                            }
                                        }
                                        """
                        )
                )),
                tools(
                        "gitlab_search_repository_candidates",
                        "gitlab_read_repository_file",
                        "gitlab_list_available_repositories",
                        "gitlab_find_class_references",
                        "gitlab_find_flow_context",
                        "gitlab_read_repository_file_chunk",
                        "gitlab_read_repository_file_chunks",
                        "gitlab_read_repository_file_outline",
                        "db_find_tables"
                )
        );

        assertTrue(policy.evidenceCoverage().hasGap("DB_CODE_GROUNDING_NEEDED"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_class_references"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_flow_context"));
        assertTrue(policy.availableToolNames().contains("gitlab_list_available_repositories"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_chunk"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_chunks"));
        assertTrue(policy.availableToolNames().contains("gitlab_read_repository_file_outline"));
        assertFalse(policy.availableToolNames().contains("gitlab_search_repository_candidates"));
        assertFalse(policy.availableToolNames().contains("gitlab_read_repository_file"));
        assertTrue(policy.availableToolNames().contains("db_find_tables"));
    }

    @Test
    void shouldNotAddDatabaseCodeGroundingGapWhenOrmMappingEvidenceIsAttached() {
        var policy = policy(
                request("dev3", List.of(
                        jpaExceptionSection(),
                        gitLabSection(
                                "CustomerEntity mapping",
                                """
                                        @Entity
                                        @Table(name = "CUSTOMERS")
                                        class CustomerEntity {
                                            @Column(name = "BUSINESS_KEY")
                                            private String businessKey;

                                            @JoinColumn(name = "CUSTOMER_ID")
                                            private CustomerEntity customer;
                                        }
                                        """
                        )
                )),
                tools("gitlab_find_class_references", "db_find_tables")
        );

        assertFalse(policy.evidenceCoverage().hasGap("DB_CODE_GROUNDING_NEEDED"));
        assertTrue(policy.availableToolNames().contains("db_find_tables"));
    }

    @Test
    void shouldDisableElasticToolsWhenLogsAreSufficient() {
        var policy = policy(
                request("dev3", List.of(sufficientElasticSection())),
                tools("elastic_search_logs_by_correlation_id")
        );

        assertEquals(List.of(), policy.availableToolNames());
    }

    @Test
    void shouldEnableElasticToolsWhenLogsAreTruncated() {
        var policy = policy(
                request("dev3", List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(item(
                                "truncated log",
                                attr("serviceName", "crm-billing-service"),
                                attr("className", "com.example.CustomerBillingService"),
                                attr("message", "Failed to settle invoice"),
                                attr("messageTruncated", "true")
                        ))
                ))),
                tools(
                        "elastic_search_logs_by_correlation_id",
                        "elastic_summarize_http_calls_by_path",
                        "elastic_fetch_http_call_logs"
                )
        );

        assertEquals(
                Set.of(
                        "elastic_search_logs_by_correlation_id",
                        "elastic_summarize_http_calls_by_path",
                        "elastic_fetch_http_call_logs"
                ),
                Set.copyOf(policy.availableToolNames())
        );
    }

    @Test
    void shouldEnableDatabaseToolsForRequiredDataDiagnosticWithResolvedEnvironmentExceptRawSql() {
        var policy = policy(
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
        var policy = policy(
                request(null, List.of(jpaExceptionSection())),
                tools("db_get_scope", "db_find_tables")
        );

        assertEquals(List.of(), policy.availableToolNames());
        assertTrue(policy.evidenceCoverage().hasGap("DB_ENVIRONMENT_UNRESOLVED"));
    }

    @Test
    void shouldDisableDatabaseToolsWhenThereIsNoIncidentDataDiagnosticNeed() {
        var policy = policy(
                request("dev3", List.of(sufficientElasticSection())),
                tools("db_get_scope", "db_find_tables")
        );

        assertEquals(List.of(), policy.availableToolNames());
    }

    @Test
    void shouldEnableOperationalContextToolsWhenOperationalContextEvidenceIsMissing() {
        var policy = policy(
                request("dev3", List.of(sufficientElasticSection())),
                tools("opctx_get_scope", "opctx_search", "opctx_get_entity")
        );

        assertTrue(policy.availableToolNames().contains("opctx_get_scope"));
        assertTrue(policy.availableToolNames().contains("opctx_search"));
        assertTrue(policy.availableToolNames().contains("opctx_get_entity"));
        assertTrue(policy.enabledCapabilityGroups().contains("operational-context"));
    }

    @Test
    void shouldKeepOperationalContextToolsForIncidentSessionsEvenWhenContextIsMatched() {
        var policy = policy(
                requestWithoutGitLabScope("dev3", List.of(sufficientElasticSection(), matchedOperationalContextSection())),
                tools("opctx_get_scope", "opctx_search")
        );

        assertEquals(Set.of("opctx_get_scope", "opctx_search"), Set.copyOf(policy.availableToolNames()));
        assertTrue(policy.disabledCapabilityGroups().stream()
                .noneMatch(group -> "operational-context".equals(group.get("name"))));
    }

    @Test
    void shouldKeepOperationalContextToolsForFlowGapEvenWhenContextIsMatched() {
        var policy = policy(
                request("dev3", List.of(
                        matchedOperationalContextSection(),
                        gitLabSection(
                                "CheckoutService.java around failing method",
                                """
                                        class CheckoutService {
                                            Customer submit(CheckoutCommand command) {
                                                return command.toOrder();
                                            }
                                        }
                                        """
                        )
                )),
                tools("opctx_search", "opctx_get_entity")
        );

        assertTrue(policy.evidenceCoverage().hasGap("MISSING_FLOW_CONTEXT"));
        assertEquals(Set.of("opctx_search", "opctx_get_entity"), Set.copyOf(policy.availableToolNames()));
    }

    @Test
    void shouldAlwaysEnableToolFeedbackForInitialAnalysisWhenRegistered() {
        var policy = policy(
                request("dev3", List.of(sufficientElasticSection())),
                tools("record_tool_feedback", "db_find_tables")
        );

        assertEquals(Set.of("record_tool_feedback"), Set.copyOf(policy.availableToolNames()));
        assertTrue(policy.toolFeedbackEnabled());
    }

    @Test
    void shouldCreateFollowUpPolicyFromResolvedChatScope() {
        var policy = policyFactory.createForFollowUp(
                chatRequest("dev3", "CRM/runtime", "release/2026.04"),
                tools(
                        "elastic_search_logs_by_correlation_id",
                        "elastic_summarize_http_calls_by_path",
                        "elastic_fetch_http_call_logs",
                        "gitlab_find_flow_context",
                        "db_find_tables",
                        "db_execute_readonly_sql",
                        "opctx_search"
                )
        );

        assertTrue(policy.availableToolNames().contains("elastic_search_logs_by_correlation_id"));
        assertTrue(policy.availableToolNames().contains("elastic_summarize_http_calls_by_path"));
        assertTrue(policy.availableToolNames().contains("elastic_fetch_http_call_logs"));
        assertTrue(policy.availableToolNames().contains("gitlab_find_flow_context"));
        assertTrue(policy.availableToolNames().contains("db_find_tables"));
        assertFalse(policy.availableToolNames().contains("db_execute_readonly_sql"));
        assertTrue(policy.availableToolNames().contains("opctx_search"));
    }

    @Test
    void shouldDisableFollowUpGitLabAndDatabaseToolsWhenScopeIsMissing() {
        var policy = policyFactory.createForFollowUp(
                chatRequest(null, null, null),
                tools(
                        "elastic_search_logs_by_correlation_id",
                        "elastic_summarize_http_calls_by_path",
                        "elastic_fetch_http_call_logs",
                        "gitlab_find_flow_context",
                        "db_find_tables",
                        "opctx_search"
                )
        );

        assertEquals(
                Set.of(
                        "elastic_search_logs_by_correlation_id",
                        "elastic_summarize_http_calls_by_path",
                        "elastic_fetch_http_call_logs",
                        "opctx_search"
                ),
                Set.copyOf(policy.availableToolNames())
        );
    }

    @Test
    void shouldAlwaysEnableToolFeedbackForFollowUpWhenRegistered() {
        var policy = policyFactory.createForFollowUp(
                chatRequest(null, null, null),
                tools("record_tool_feedback", "gitlab_find_flow_context", "db_find_tables")
        );

        assertEquals(Set.of("record_tool_feedback"), Set.copyOf(policy.availableToolNames()));
        assertTrue(policy.toolFeedbackEnabled());
    }

    private CopilotIncidentToolAccessPolicy policy(
            InitialAnalysisRequest request,
            List<ToolDefinition> tools
    ) {
        return policyFactory.create(request, tools);
    }

    private InitialAnalysisRequest request(String environment, List<AnalysisEvidenceSection> sections) {
        return new InitialAnalysisRequest(
                "corr-123",
                environment,
                "release/2026.04",
                "CRM/runtime",
                sections
        );
    }

    private InitialAnalysisRequest requestWithoutGitLabScope(String environment, List<AnalysisEvidenceSection> sections) {
        return new InitialAnalysisRequest(
                "corr-123",
                environment,
                null,
                null,
                sections
        );
    }

    private AnalysisAiChatRequest chatRequest(String environment, String gitLabGroup, String gitLabBranch) {
        return new AnalysisAiChatRequest(
                "corr-123",
                environment,
                gitLabBranch,
                gitLabGroup,
                List.of(),
                List.of(),
                null,
                List.of(),
                "What next?",
                null
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
                        attr("serviceName", "crm-billing-service"),
                        attr("className", "com.example.CustomerBillingService"),
                        attr("message", "Failed to settle invoice"),
                        attr("exception", """
                                java.lang.IllegalStateException: failed
                                \tat com.example.CustomerBillingService.settle(CustomerBillingService.java:42)
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
                        attr("serviceName", "crm-billing-service"),
                        attr("message", "EntityNotFoundException while loading tenant status by business key")
                ))
        );
    }

    private AnalysisEvidenceSection matchedOperationalContextSection() {
        return new AnalysisEvidenceSection(
                "operational-context",
                "matched-context",
                List.of(item(
                        "Payments system",
                        attr("systemId", "payments"),
                        attr("name", "Payments")
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
