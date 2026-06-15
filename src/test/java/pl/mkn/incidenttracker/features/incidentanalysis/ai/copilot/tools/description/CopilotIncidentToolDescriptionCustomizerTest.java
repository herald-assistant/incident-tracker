package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.description;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentToolDescriptionCustomizerTest {

    private final CopilotIncidentToolDescriptionCustomizer customizer = new CopilotIncidentToolDescriptionCustomizer(
            new CopilotIncidentToolGuidanceCatalog()
    );

    @Test
    void shouldAppendGuidanceForExpensiveGitLabFileRead() {
        var description = customizer.customize("gitlab_read_repository_file", "Read repository file.");

        assertTrue(description.contains("Read repository file."));
        assertTrue(description.contains("Copilot guidance:"));
        assertTrue(description.contains("Expensive. Use only when outline/chunk tools are insufficient."));
        assertTrue(description.contains("Prefer gitlab_read_repository_file_chunk"));
        assertTrue(description.contains("Do not use for broad browsing."));
    }

    @Test
    void shouldAppendGuidanceForGitLabFilesByPathRead() {
        var description = customizer.customize("gitlab_read_repository_files_by_path", "Read files by path.");

        assertTrue(description.contains("grounded file list"));
        assertTrue(description.contains("gitlab_build_endpoint_use_case_context"));
        assertTrue(description.contains("Pass only files that are relevant"));
    }

    @Test
    void shouldAppendGuidanceForRawSql() {
        var description = customizer.customize("db_execute_readonly_sql", "Executes SQL.");

        assertTrue(description.contains("Last resort only."));
        assertTrue(description.contains("Use typed DB tools first."));
        assertTrue(description.contains("May be disabled by runtime policy and budget."));
    }

    @Test
    void shouldAppendCodeGroundingGuidanceForDbDiscovery() {
        var description = customizer.customize("db_find_tables", "Finds tables.");

        assertTrue(description.contains("ground the entity/repository/table mapping"));
        assertTrue(description.contains("use DB discovery as fallback"));
        assertTrue(description.contains("Use code-derived table, column and relation hints"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendCrossRepositoryGuidanceForGitLabClassReferences() {
        var description = customizer.customize("gitlab_find_class_references", "Finds class references.");

        assertTrue(description.contains("focused retry across the matching operational-context codeSearchScope"));
        assertTrue(description.contains("before declaring it missing"));
    }

    @Test
    void shouldAppendOperationalContextGuidance() {
        var description = customizer.customize("opctx_get_entity", "Gets operational context entity.");

        assertTrue(description.contains("Use operational context as catalog grounding and scope guidance"));
        assertTrue(description.contains("Use opctx_get_entity before relying on ownership"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendElasticHttpDiagnosticGuidance() {
        var description = customizer.customize(
                "elastic_fetch_http_call_logs",
                "Fetches HTTP call logs."
        );

        assertTrue(description.contains("If path is omitted, the current hidden incident correlationId is used."));
        assertTrue(description.contains("comparison calls"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldLeaveUnknownToolDescriptionUntouched() {
        var description = customizer.customize("custom_tool", "  Custom description.  ");

        assertEquals("Custom description.", description);
    }
}
