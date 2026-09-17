package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.assertj.core.api.Assertions.assertThat;

class UxInspectorToolDescriptionCustomizerTest {

    private final UxInspectorToolDescriptionCustomizer customizer = new UxInspectorToolDescriptionCustomizer();

    @Test
    void shouldPreferSemanticOpenApiReadOverFileChunks() {
        var description = customizer.customize(
                CopilotToolDescriptionContext.profile("ux-inspector"),
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                "Read OpenAPI operation."
        );

        assertThat(description)
                .contains("OpenAPI/Swagger")
                .contains("instead of reading the contract with full-file or chunk tools")
                .contains("pinned revision");
    }

    @Test
    void shouldDescribeNaturalTypeScriptContinuationWithoutSyntheticReferences() {
        var description = customizer.customize(
                CopilotToolDescriptionContext.profile("ux-inspector"),
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                "Read a focused TypeScript symbol slice."
        );

        assertThat(description)
                .contains("direct file/type", "consumer import coordinates", "visible original code", "memberNames")
                .contains("hidden context")
                .doesNotContain("sliceRef");
    }
}
