package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDriftViewerCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT =
            Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDeclareTheFeatureSkillNamedByThePrompt() {
        assertThat(ConfigDriftViewerCopilotRuntimeSkillNames.featureSkillNames())
                .containsExactly("config-drift-viewer-deep-review");
    }

    @Test
    void shouldKeepDeepSkillAlignedWithCompactArtifactV1() throws Exception {
        var deep = skill(ConfigDriftViewerCopilotRuntimeSkillNames.DEEP_REVIEW);

        assertThat(deep).contains(
                "name: config-drift-viewer-deep-review",
                "`configuration-tree.yaml`",
                "`changes.json`",
                "`deep-context.json`",
                "representation code `M`",
                "`differenceId`/`findingId`"
        );
        assertThat(deep).doesNotContain(
                "differences-and-findings.json",
                "manifest-index.json",
                "runtime-configuration/manifest/",
                "runtime-configuration-basic-review",
                "`MASKED`",
                "`valueToken`",
                "C:\\",
                "/Users/"
        );
        assertThat(Files.exists(
                SKILLS_ROOT.resolve("runtime-configuration-basic-review").resolve("SKILL.md")
        )).isFalse();
    }

    private String skill(String name) throws Exception {
        var skillFile = SKILLS_ROOT.resolve(name).resolve("SKILL.md");
        assertThat(Files.isRegularFile(skillFile))
                .as("Missing Runtime Configuration runtime skill: %s", name)
                .isTrue();
        return Files.readString(skillFile);
    }
}
