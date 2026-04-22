package pl.mkn.incidenttracker.analysis.mcp.database;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "analysis.database.enabled=true")
class DatabaseMcpToolsContextTest {

    @Autowired
    private ToolCallbackProvider[] toolCallbackProviders;

    @Test
    void shouldRegisterDatabaseMcpToolsWhenCapabilityIsEnabled() {
        var toolNames = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(toolNames.containsAll(Set.of(
                "db_get_scope",
                "db_find_tables",
                "db_find_columns",
                "db_describe_table",
                "db_exists_by_key",
                "db_count_rows",
                "db_group_count",
                "db_sample_rows",
                "db_check_orphans",
                "db_find_relationships",
                "db_join_count",
                "db_join_sample",
                "db_compare_table_to_expected_mapping",
                "db_execute_readonly_sql"
        )));
    }
}
