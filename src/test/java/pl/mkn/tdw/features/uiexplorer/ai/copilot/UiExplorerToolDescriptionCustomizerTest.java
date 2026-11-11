package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.assertj.core.api.Assertions.assertThat;

class UiExplorerToolDescriptionCustomizerTest {

    @Test void historyGuidanceBelongsToToolDescriptionsAndSelectedProfile() {
        for (var name : java.util.List.of(
                pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_COMMITS,
                pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_COMMIT_DIFF)) {
            var description = customizer.customize(
                    pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext.profile("ui-explorer"),
                    name, "Neutral history contract.");
            org.assertj.core.api.Assertions.assertThat(description).startsWith("Neutral history contract.")
                    .isNotEqualTo("Neutral history contract.").doesNotContain("Pass branchRef explicitly");
            org.assertj.core.api.Assertions.assertThat(customizer.customize(
                    pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext.profile("other-feature"),
                    name, "Neutral history contract.")).isEqualTo("Neutral history contract.");
        }
    }

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
                .contains("fallback-only", "exact branchRef", "never guess repository", "untrusted source evidence")
                .contains("no safe frontend", "natural TypeScript file/import target");
        assertThat(unchanged).isEqualTo("Read a source chunk.");
    }

    @Test
    void shouldPreferNaturalTypeScriptCoordinatesInUiExplorer() {
        var customized = customizer.customize(
                CopilotToolDescriptionContext.profile("ui-explorer"),
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                "Read a focused TypeScript symbol slice."
        );

        assertThat(customized)
                .contains("prefer this deterministic tool")
                .contains("natural file/type target")
                .contains("current original source contains a material import")
                .contains("memberNames")
                .contains("hidden runtime context")
                .doesNotContain("sliceRef");
    }
}
