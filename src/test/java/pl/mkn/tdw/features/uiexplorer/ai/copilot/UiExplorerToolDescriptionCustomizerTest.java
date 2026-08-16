package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.assertj.core.api.Assertions.assertThat;

class UiExplorerToolDescriptionCustomizerTest {

    private final UiExplorerToolDescriptionCustomizer customizer = new UiExplorerToolDescriptionCustomizer();

    @Test
    void shouldAddFallbackAndTrustGuidanceOnlyForUiExplorerRuntimeContext() {
        var customized = customizer.customize(
                CopilotToolDescriptionContext.profile("ui-explorer"),
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                "Read a source chunk."
        );
        var unchanged = customizer.customize(
                CopilotToolDescriptionContext.profile("incident-analysis"),
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                "Read a source chunk."
        );

        assertThat(customized)
                .contains("fallback-only", "exact branchRef", "never guess repository", "untrusted source evidence");
        assertThat(unchanged).isEqualTo("Read a source chunk.");
    }
}
