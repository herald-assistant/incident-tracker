package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.database.DatabaseToolService;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseMcpTools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolBridge;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;
import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.DbCountResult;
import static pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.DbTableRef;

class CopilotSdkDatabaseToolBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldHideEnvironmentAndSchemaPatternFromDiscoveryToolSchemas() {
        var bridge = toolBridge(
                java.util.List.of(databaseToolProvider()),
                objectMapper,
                toolEvidenceCaptureRegistry(objectMapper),
                metricsRegistry(),
                metricsLogger()
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
                Set.of("applicationNamePattern", "tableNamePattern", "entityOrKeywordHint", "limit", "reason"),
                Set.of("environment", "schemaPattern", "toolContext", "request")
        );
        assertSchemaProperties(
                toolsByName.get("db_find_columns"),
                Set.of("applicationNamePattern", "tableNamePattern", "columnNamePattern", "javaFieldNameHint", "limit", "reason"),
                Set.of("environment", "schemaPattern", "toolContext", "request")
        );
        assertSchemaProperties(
                toolsByName.get("db_execute_readonly_sql"),
                Set.of("sql", "reason", "maxRows"),
                Set.of("environment", "toolContext", "request")
        );
    }

    @Test
    void shouldCaptureDatabaseToolArgumentsAndResultAsAiToolEvidence() {
        var databaseToolService = mock(DatabaseToolService.class);
        when(databaseToolService.countRows(any(), any())).thenReturn(new DbCountResult(
                "zt002",
                "oracle",
                new DbTableRef("CLP", "ORDER_EVENT"),
                3L,
                List.of("CORRELATION_ID = corr-123"),
                List.of()
        ));

        var registry = toolEvidenceCaptureRegistry(objectMapper);
        var bridge = toolBridge(
                List.of(databaseToolProvider(databaseToolService)),
                objectMapper,
                registry,
                metricsRegistry(),
                metricsLogger()
        );
        var sessionContext = sessionContext();
        var tool = bridge.buildToolDefinitions(sessionContext).stream()
                .filter(candidate -> candidate.name().equals("db_count_rows"))
                .findFirst()
                .orElseThrow();
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession(sessionContext.copilotSessionId(), new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        });

        var invocation = new ToolInvocation()
                .setSessionId(sessionContext.copilotSessionId())
                .setToolCallId("tool-call-db-1")
                .setToolName("db_count_rows")
                .setArguments(objectMapper.valueToTree(Map.of(
                        "table", Map.of("schema", "CLP", "tableName", "ORDER_EVENT"),
                        "reason", "Sprawdzam liczbe rekordow dla korelacji.",
                        "filters", List.of(Map.of(
                                "column", "correlation_id",
                                "operator", "EQ",
                                "values", List.of("corr-123")
                        ))
                )));

        var result = tool.handler().invoke(invocation).join();

        assertInstanceOf(Map.class, result);
        assertNotNull(capturedSection.get());
        assertEquals("database", capturedSection.get().provider());
        assertEquals("tool-results", capturedSection.get().category());
        assertEquals(1, capturedSection.get().items().size());
        assertEquals("db_count_rows", capturedSection.get().items().get(0).title());

        var attributes = capturedSection.get().items().get(0).attributes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value()
                ));

        assertEquals("Sprawdzam liczbe rekordow dla korelacji.", attributes.get("reason"));
        assertTrue(attributes.get("result").contains("\"count\" : 3"));
        assertTrue(attributes.get("result").contains("\"schema\" : \"CLP\""));
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

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime"
        );
    }

    private ToolCallbackProvider databaseToolProvider() {
        return databaseToolProvider(mock(DatabaseToolService.class));
    }

    private ToolCallbackProvider databaseToolProvider(DatabaseToolService databaseToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new DatabaseMcpTools(databaseToolService))
                .build();
    }

    private CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }

    private CopilotMetricsLogger metricsLogger() {
        return new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);
    }
}
