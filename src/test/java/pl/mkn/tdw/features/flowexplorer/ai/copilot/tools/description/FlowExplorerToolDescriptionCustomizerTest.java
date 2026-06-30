package pl.mkn.tdw.features.flowexplorer.ai.copilot.tools.description;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(description.contains("method body, conditions, helper logic or mapper details"));
        assertTrue(description.contains("use gitlab_build_java_method_use_case_context instead"));
        assertTrue(description.contains("Check snippet-cards.md first"));
        assertTrue(description.contains("canonical-tool-inputs.md"));
        assertTrue(description.contains("compact-flow-manifest.md"));
        assertTrue(description.contains("methodSelectors"));
        assertTrue(description.contains("lineStart is optional"));
        assertTrue(description.contains("Always provide reason as one short Polish sentence"));
    }

    @Test
    void shouldAppendFlowExplorerGuidanceForJavaMethodUseCaseContext() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT,
                "Builds Java method use-case context."
        );

        assertTrue(description.contains("Builds Java method use-case context."));
        assertTrue(description.contains("Flow Explorer guidance:"));
        assertTrue(description.contains("downstream use-case path is still incomplete"));
        assertTrue(description.contains("continue flow from the known method"));
        assertTrue(description.contains("maxResults"));
        assertFalse(description.contains("focusHints"));
        assertFalse(description.contains("maxFiles"));
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
        assertTrue(description.contains("method context, method slice, outline or focused chunks"));
    }

    @Test
    void shouldAppendPersistenceGuidanceForFileOutline() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
                "Reads repository file outline."
        );

        assertTrue(description.contains("class structure, annotations, inheritance"));
        assertTrue(description.contains("recursively close every updated table"));
        assertTrue(description.contains("@OneToMany/@ManyToMany/@ElementCollection/@JoinTable"));
        assertTrue(description.contains("do not read DDL/Liquibase/Flyway/changelog files"));
        assertTrue(description.contains("infer table and column names from Java implementation"));
        assertTrue(description.contains("ORM naming conventions"));
        assertTrue(description.contains("Treat @Column as optional"));
        assertTrue(description.contains("do not report standard ORM naming inference as a visibility limit"));
        assertTrue(description.contains("gitlab_build_java_method_use_case_context"));
        assertTrue(description.contains("gitlab_read_java_method_slice"));
        assertTrue(description.contains("fieldSummaries"));
        assertTrue(description.contains("typeSummaries"));
        assertTrue(description.contains("constructorSummaries"));
        assertTrue(description.contains("methodSummaries"));
        assertTrue(description.contains("annotations are attached to the Java element"));
    }

    @Test
    void shouldAppendPersistenceDeepGuidanceForFileChunks() {
        var description = customizer.customize(
                FLOW_EXPLORER_CONTEXT,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                "Reads repository file chunk."
        );

        assertTrue(description.contains("close exact ORM annotation gaps"));
        assertTrue(description.contains("every table and column touched by the endpoint"));
        assertTrue(description.contains("do not stop at join-table IDs"));
        assertTrue(description.contains("Do not use chunks to read DDL/Liquibase/Flyway/changelog files"));
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
