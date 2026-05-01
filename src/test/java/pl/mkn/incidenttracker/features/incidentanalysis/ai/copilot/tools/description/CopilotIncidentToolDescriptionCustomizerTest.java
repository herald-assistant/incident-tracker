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

        assertTrue(description.contains("focused retry across operational-context codeSearchProjects"));
        assertTrue(description.contains("before declaring it missing"));
    }

    @Test
    void shouldLeaveUnknownToolDescriptionUntouched() {
        var description = customizer.customize("custom_tool", "  Custom description.  ");

        assertEquals("Custom description.", description);
    }
}
