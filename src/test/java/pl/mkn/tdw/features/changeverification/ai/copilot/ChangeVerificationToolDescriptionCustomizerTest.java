package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationToolDescriptionCustomizerTest {

    private final ChangeVerificationToolDescriptionCustomizer customizer =
            new ChangeVerificationToolDescriptionCustomizer();

    @Test
    void shouldAddGitLabProjectNameGuidanceForChangeVerificationProfile() {
        var description = customizer.customize(
                CopilotToolDescriptionContext.profile("change-verification"),
                GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                "Read files."
        );

        assertThat(description)
                .contains("Change Verification guidance:")
                .contains("Use projectName exactly from change-verification/repository-scope.md")
                .contains("Do not pass projectPath, gitLabPath, rootGroup/projectName or the full merge-request path as projectName.")
                .contains("Pass branchRef from analysisRef in change-verification/repository-scope.md");
    }

    @Test
    void shouldKeepBaseDescriptionForOtherProfiles() {
        var description = customizer.customize(
                CopilotToolDescriptionContext.profile("incident-analysis"),
                GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                "Read files."
        );

        assertThat(description).isEqualTo("Read files.");
    }
}
