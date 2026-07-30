package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT =
            Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldSelectOnlyTheReviewSkillForRequestedMode() {
        assertThat(RuntimeConfigurationCopilotRuntimeSkillNames.forMode(
                RuntimeConfigurationVerificationMode.BASIC
        )).containsExactly("runtime-configuration-basic-review");
        assertThat(RuntimeConfigurationCopilotRuntimeSkillNames.forMode(
                RuntimeConfigurationVerificationMode.DEEP
        )).containsExactly("runtime-configuration-deep-review");
    }

    @Test
    void shouldKeepBothSkillsAlignedWithCompactArtifactV2() throws Exception {
        var basic = skill(RuntimeConfigurationCopilotRuntimeSkillNames.BASIC_REVIEW);
        var deep = skill(RuntimeConfigurationCopilotRuntimeSkillNames.DEEP_REVIEW);

        assertThat(basic).contains(
                "name: runtime-configuration-basic-review",
                "`configuration-tree.yaml`",
                "`changes.json`",
                "`documentColumns`",
                "`differenceColumns`",
                "`p:*`",
                "kod `M`"
        );
        assertThat(deep).contains(
                "name: runtime-configuration-deep-review",
                "`configuration-tree.yaml`",
                "`changes.json`",
                "`deep-context.json`",
                "representation code `M`"
        );
        assertThat(basic + deep).doesNotContain(
                "differences-and-findings.json",
                "manifest-index.json",
                "runtime-configuration/manifest/",
                "`MASKED`",
                "`valueToken`",
                "C:\\",
                "/Users/"
        );
    }

    private String skill(String name) throws Exception {
        var skillFile = SKILLS_ROOT.resolve(name).resolve("SKILL.md");
        assertThat(Files.isRegularFile(skillFile))
                .as("Missing Runtime Configuration runtime skill: %s", name)
                .isTrue();
        return Files.readString(skillFile);
    }
}
