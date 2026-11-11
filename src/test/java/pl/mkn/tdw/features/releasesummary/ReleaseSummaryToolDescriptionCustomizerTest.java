package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryToolDescriptionCustomizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseSummaryToolDescriptionCustomizerTest {
    @Test void historyGuidancePreservesNeutralContractAndOnlyAppliesToReleaseSummary() {
        var customizer = new ReleaseSummaryToolDescriptionCustomizer();
        for (var tool : List.of(GitLabToolNames.LIST_REPOSITORY_COMMITS, GitLabToolNames.READ_COMMIT_DIFF,
                GitLabToolNames.READ_REPOSITORY_FILE, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK)) {
            assertThat(customizer.customize(CopilotToolDescriptionContext.profile("release-summary"), tool, "Neutral contract."))
                    .startsWith("Neutral contract.").contains("Release summary:");
            assertThat(customizer.customize(CopilotToolDescriptionContext.profile("other-feature"), tool, "Neutral contract."))
                    .isEqualTo("Neutral contract.");
        }
        assertThat(customizer.customize(CopilotToolDescriptionContext.profile("release-summary"),
                GitLabToolNames.LIST_REPOSITORY_COMMITS, "Neutral contract.")).contains("target SHA", "manifestu");
    }
}
