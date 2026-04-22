package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.database.DatabaseToolService;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceCaptureRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseMcpTools;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CopilotSdkDatabaseToolBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldHideEnvironmentAndSchemaPatternFromDiscoveryToolSchemas() {
        var bridge = new CopilotSdkToolBridge(
                java.util.List.of(databaseToolProvider()),
                objectMapper,
                new CopilotToolEvidenceCaptureRegistry(objectMapper)
        );
        var toolsByName = bridge.buildToolDefinitions(new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime"
        )).stream().collect(java.util.stream.Collectors.toMap(ToolDefinition::name, tool -> tool));

        assertSchemaProperties(
                toolsByName.get("db_find_tables"),
                Set.of("applicationNamePattern", "tableNamePattern", "entityOrKeywordHint", "limit"),
                Set.of("environment", "schemaPattern", "toolContext", "request")
        );
        assertSchemaProperties(
                toolsByName.get("db_find_columns"),
                Set.of("applicationNamePattern", "tableNamePattern", "columnNamePattern", "javaFieldNameHint", "limit"),
                Set.of("environment", "schemaPattern", "toolContext", "request")
        );
        assertSchemaProperties(
                toolsByName.get("db_execute_readonly_sql"),
                Set.of("sql", "reason", "maxRows"),
                Set.of("environment", "toolContext", "request")
        );
    }

    private void assertSchemaProperties(
            ToolDefinition tool,
            Set<String> expectedProperties,
            Set<String> forbiddenProperties
    ) {
        assertNotNull(tool);
        assertInstanceOf(Map.class, tool.parameters());

        @SuppressWarnings("unchecked")
        var parameters = (Map<String, Object>) tool.parameters();
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) parameters.get("properties");

        assertNotNull(properties);
        assertTrue(properties.keySet().containsAll(expectedProperties));
        forbiddenProperties.forEach(property -> assertFalse(properties.containsKey(property)));
    }

    private ToolCallbackProvider databaseToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new DatabaseMcpTools(mock(DatabaseToolService.class)))
                .build();
    }
}
