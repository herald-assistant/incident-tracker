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
        assertTrue(description.contains("canonical-tool-inputs.md"));
        assertTrue(description.contains("compact-flow-manifest.md"));
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
    void shouldAppendFlowExplorerGuidanceForOpenApiEndpointSlice() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                "Reads OpenAPI endpoint slice."
        );

        assertTrue(description.contains("OpenAPI/Swagger YAML"));
        assertTrue(description.contains("Prefer this over gitlab_read_repository_file"));
        assertTrue(description.contains("schemaDepth"));
    }

    @Test
    void shouldWarnThatFullFileReadsMayBeOnlyPrefix() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_REPOSITORY_FILE,
                "Reads repository file."
        );

        assertTrue(description.contains("maxCharacters returns a prefix of the file"));
        assertTrue(description.contains("if truncated=true"));
        assertTrue(description.contains("gitlab_read_repository_file_chunk"));
    }

    @Test
    void shouldAppendPersistenceGuidanceForFileOutline() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
                "Reads repository file outline."
        );

        assertTrue(description.contains("method names before choosing a focused read"));
        assertTrue(description.contains("deep persistence analysis"));
        assertTrue(description.contains("fieldSummaries"));
        assertTrue(description.contains("typeSummaries"));
        assertTrue(description.contains("constructorSummaries"));
        assertTrue(description.contains("methodSummaries"));
        assertTrue(description.contains("annotations are attached to the Java element"));
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
