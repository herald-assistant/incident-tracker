package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDeclareFeatureSkillNamesUsedByThePromptWorkflow() {
        assertThat(ChangeVerificationCopilotRuntimeSkillNames.featureSkillNames()).containsExactly(
                "change-verification-orchestrator",
                "change-verification-compliance-check"
        );
    }

    @Test
    void shouldKeepFeatureSkillFilesAlignedWithRuntimeContract() throws Exception {
        for (var skillName : ChangeVerificationCopilotRuntimeSkillNames.featureSkillNames()) {
            var skillFile = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");
            assertThat(Files.isRegularFile(skillFile))
                    .as("Missing Change Verification runtime skill: %s", skillName)
                    .isTrue();
            var content = Files.readString(skillFile);
            assertThat(content).contains("name: " + skillName);
        }

        assertThat(skill("change-verification-orchestrator")).contains(
                "jeden kanoniczny rejestr",
                "additionalChecks",
                "Nie uruchamiaj report tools"
        );
        assertThat(skill("change-verification-compliance-check")).contains(
                "source.quote",
                "SATISFIED",
                "NOT_SATISFIED",
                "NOT_VERIFIED",
                "affectedRuleIds"
        );
    }

    @Test
    void shouldKeepRuntimeSkillsFeatureScopedAndFreeFromDatabaseChecks() throws Exception {
        for (var skillName : ChangeVerificationCopilotRuntimeSkillNames.featureSkillNames()) {
            var content = skill(skillName);
            assertThat(content).doesNotContain("features.incidentanalysis", "features.flowexplorer");
            assertThat(content).doesNotContain("C:\\", "/Users/");
            assertThat(content).doesNotContain("report_upsert_section", "report_get_current");
        }
    }

    private String skill(String name) throws Exception {
        return Files.readString(SKILLS_ROOT.resolve(name).resolve("SKILL.md"));
    }
}
