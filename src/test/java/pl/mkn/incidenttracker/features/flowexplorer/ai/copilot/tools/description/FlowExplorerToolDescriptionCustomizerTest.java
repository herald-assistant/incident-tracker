package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.tools.description;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;
import pl.mkn.incidenttracker.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerToolDescriptionCustomizerTest {

    private final FlowExplorerToolDescriptionCustomizer customizer = new FlowExplorerToolDescriptionCustomizer();
    private static final CopilotToolDescriptionContext FLOW_EXPLORER_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");
    private static final CopilotToolDescriptionContext INCIDENT_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    @Test
    void shouldAppendFlowExplorerGuidanceForJavaMethodSlice() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_JAVA_METHOD_SLICE,
                "Reads Java method slice."
        );

        assertTrue(description.contains("Reads Java method slice."));
        assertTrue(description.contains("Flow Explorer guidance:"));
        assertTrue(description.contains("snippet-cards.md is insufficient"));
        assertTrue(description.contains("methodSelectors"));
        assertTrue(description.contains("lineStart is optional"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendFlowExplorerGuidanceForOperationalContextTools() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                OperationalContextToolNames.GET_ENTITY,
                "Gets operational context entity."
        );

        assertTrue(description.contains("catalog grounding"));
        assertTrue(description.contains("code-search scope"));
        assertTrue(description.contains("code evidence wins for endpoint flow"));
    }

    @Test
    void shouldNotAppendFlowExplorerGuidanceForIncidentSession() {
        var description = customizer.customize(
                INCIDENT_CONTEXT,
                GitLabToolNames.READ_JAVA_METHOD_SLICE,
                "Reads Java method slice."
        );

        assertEquals("Reads Java method slice.", description);
    }

    @Test
    void shouldLeaveUnknownToolDescriptionUntouched() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                "custom_tool",
                "  Custom description.  "
        );

        assertEquals("Custom description.", description);
    }
}
