package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "analysis.operational-context.enabled=true")
class OperationalContextMcpToolsContextTest {

    @Autowired
    private ToolCallbackProvider[] toolCallbackProviders;

    @Test
    void shouldRegisterOperationalContextMcpToolsWhenCapabilityIsEnabled() {
        var toolNames = Arrays.stream(toolCallbackProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(toolNames.containsAll(Set.of(
                "opctx_get_scope",
                "opctx_list_entities",
                "opctx_search",
                "opctx_get_entity"
        )));
    }
}
