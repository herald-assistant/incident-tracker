package pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.description;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentToolDescriptionCustomizerTest {

    private final CopilotIncidentToolDescriptionCustomizer customizer = new CopilotIncidentToolDescriptionCustomizer(
            new CopilotIncidentToolGuidanceCatalog()
    );
    private static final CopilotToolDescriptionContext INCIDENT_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");
    private static final CopilotToolDescriptionContext FLOW_EXPLORER_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    @Test
    void shouldAppendGuidanceForExpensiveGitLabFileRead() {
        var description = customizer.customize(INCIDENT_CONTEXT, "gitlab_read_repository_file", "Read repository file.");

        assertTrue(description.contains("Read repository file."));
        assertTrue(description.contains("Copilot guidance:"));
        assertTrue(description.contains("Expensive. Use only when outline/chunk tools are insufficient."));
        assertTrue(description.contains("gitlab_build_java_method_use_case_context"));
        assertTrue(description.contains("Prefer gitlab_read_java_method_slice"));
        assertTrue(description.contains("Pass branchRef explicitly"));
        assertTrue(description.contains("Do not pass gitLabGroup"));
        assertTrue(description.contains("Do not use for broad browsing."));
    }

    @Test
    void shouldAppendGuidanceForGitLabFilesByPathRead() {
        var description = customizer.customize(INCIDENT_CONTEXT, "gitlab_read_repository_files_by_path", "Read files by path.");

        assertTrue(description.contains("grounded file list"));
        assertTrue(description.contains("gitlab_build_endpoint_use_case_context"));
        assertTrue(description.contains("Pass only files that are relevant"));
        assertTrue(description.contains("Pass known projectName values"));
    }

    @Test
    void shouldAppendGuidanceForGitLabJavaMethodSlice() {
        var description = customizer.customize(
                INCIDENT_CONTEXT,
                "gitlab_read_java_method_slice",
                "Reads Java methods."
        );

        assertTrue(description.contains("Focused read for a known Java file and method body."));
        assertTrue(description.contains("lineStart is optional"));
        assertTrue(description.contains("method body, predicate, mapper, validator, helper or client call"));
        assertTrue(description.contains("downstream use-case flow"));
        assertTrue(description.contains("Pass branchRef explicitly"));
        assertTrue(description.contains("Do not pass gitLabGroup"));
    }

    @Test
    void shouldAppendGuidanceForGitLabJavaMethodUseCaseContext() {
        var description = customizer.customize(
                INCIDENT_CONTEXT,
                "gitlab_build_java_method_use_case_context",
                "Builds Java method context."
        );

        assertTrue(description.contains("concrete Java class and method"));
        assertTrue(description.contains("downstream use-case flow"));
        assertTrue(description.contains("what happens after a known method"));
        assertTrue(description.contains("maxResults"));
        assertFalse(description.contains("focusHints"));
        assertFalse(description.contains("maxFiles"));
        assertFalse(description.contains("class by class"));
        assertTrue(description.contains("Do not pass gitLabGroup"));
    }

    @Test
    void shouldAppendSpringJpaGuidanceForGitLabOutlineAndFlowContext() {
        var outlineDescription = customizer.customize(
                INCIDENT_CONTEXT,
                "gitlab_read_repository_file_outline",
                "Reads outline."
        );
        var flowDescription = customizer.customize(
                INCIDENT_CONTEXT,
                "gitlab_find_flow_context",
                "Finds flow."
        );

        assertTrue(outlineDescription.contains("class role, annotations, inheritance, fields"));
        assertTrue(outlineDescription.contains("JPA/Hibernate mapping"));
        assertTrue(outlineDescription.contains("migration files"));
        assertTrue(flowDescription.contains("Feign"));
        assertTrue(flowDescription.contains("StreamBridge"));
        assertTrue(flowDescription.contains("Consumer/Function/Supplier"));
    }

    @Test
    void shouldAppendGuidanceForRawSql() {
        var description = customizer.customize(INCIDENT_CONTEXT, "db_execute_readonly_sql", "Executes SQL.");

        assertTrue(description.contains("Last resort only."));
        assertTrue(description.contains("Use typed DB tools first."));
        assertTrue(description.contains("May be disabled by runtime policy and budget."));
    }

    @Test
    void shouldAppendCodeGroundingGuidanceForDbDiscovery() {
        var description = customizer.customize(INCIDENT_CONTEXT, "db_find_tables", "Finds tables.");

        assertTrue(description.contains("ground the entity/repository/table mapping"));
        assertTrue(description.contains("use DB discovery as fallback"));
        assertTrue(description.contains("Use code-derived table, column and relation hints"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendCrossRepositoryGuidanceForGitLabClassReferences() {
        var description = customizer.customize(INCIDENT_CONTEXT, "gitlab_find_class_references", "Finds class references.");

        assertTrue(description.contains("focused retry across the matching operational-context codeSearchScope"));
        assertTrue(description.contains("before declaring it missing"));
    }

    @Test
    void shouldAppendOperationalContextGuidance() {
        var description = customizer.customize(INCIDENT_CONTEXT, "opctx_get_entity", "Gets operational context entity.");

        assertTrue(description.contains("Use operational context as catalog grounding and scope guidance"));
        assertTrue(description.contains("Use opctx_get_entity before relying on ownership"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendElasticHttpDiagnosticGuidance() {
        var description = customizer.customize(
                INCIDENT_CONTEXT,
                "elastic_fetch_http_call_logs",
                "Fetches HTTP call logs."
        );

        assertTrue(description.contains("If path is omitted, the current hidden incident correlationId is used."));
        assertTrue(description.contains("comparison calls"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldLeaveUnknownToolDescriptionUntouched() {
        var description = customizer.customize(INCIDENT_CONTEXT, "custom_tool", "  Custom description.  ");

        assertEquals("Custom description.", description);
    }

    @Test
    void shouldNotAppendIncidentGuidanceForFlowExplorerSession() {
        var description = customizer.customize(FLOW_EXPLORER_CONTEXT, "gitlab_read_repository_file", "Read repository file.");

        assertEquals("Read repository file.", description);
    }
}
