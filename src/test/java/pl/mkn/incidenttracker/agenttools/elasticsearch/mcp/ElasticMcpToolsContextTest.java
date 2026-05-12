package pl.mkn.incidenttracker.agenttools.elasticsearch.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ElasticMcpToolsContextTest {

    @Autowired
    private ToolCallbackProvider[] toolCallbackProviders;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterElasticMcpToolsInSpringAi() {
        var toolNames = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(toolNames.containsAll(Set.of(
                "elastic_search_logs_by_correlation_id",
                "elastic_summarize_http_calls_by_path",
                "elastic_fetch_http_call_logs"
        )));
    }

    @Test
    void shouldKeepCurrentCorrelationIdHiddenForNewHttpDiagnosticTools() throws Exception {
        var callbacksByName = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool));

        assertSchemaPropertiesDoNotContain(callbacksByName.get("elastic_summarize_http_calls_by_path"), "correlationId");
        assertSchemaPropertiesDoNotContain(callbacksByName.get("elastic_fetch_http_call_logs"), "correlationId");
        assertSchemaPropertiesDoNotContain(callbacksByName.get("elastic_fetch_http_call_logs"), "toolContext");
    }

    private void assertSchemaPropertiesDoNotContain(ToolCallback callback, String propertyName) throws Exception {
        assertNotNull(callback);
        var schema = objectMapper.readValue(callback.getToolDefinition().inputSchema(), Map.class);
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) schema.get("properties");

        assertNotNull(properties);
        assertFalse(properties.containsKey(propertyName));
    }

}
