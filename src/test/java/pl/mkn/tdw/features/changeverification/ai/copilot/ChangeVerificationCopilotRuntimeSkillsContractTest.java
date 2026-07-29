package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDeclareOrchestratorSectionAndWriteReportSkillsForCompliance() {
        assertThat(ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames()).containsExactly(
                "change-verification-orchestrator",
                "change-verification-compliance-check",
                "change-verification-story-compliance-section",
                "change-verification-instruction-compliance-section",
                "change-verification-write-report"
        );
        assertThat(ChangeVerificationCopilotRuntimeSkillNames.smokePackSkillNames())
                .containsExactly("change-verification-smoke-pack-design");
    }

    @Test
    void shouldSelectOnlyRequestedComplianceSectionSkills() {
        var storyOnly = new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
                true,
                false,
                null,
                null,
                null
        );

        assertThat(ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames(storyOnly))
                .contains(ChangeVerificationCopilotRuntimeSkillNames.STORY_COMPLIANCE_SECTION)
                .doesNotContain(ChangeVerificationCopilotRuntimeSkillNames.INSTRUCTION_COMPLIANCE_SECTION)
                .endsWith(ChangeVerificationCopilotRuntimeSkillNames.WRITE_REPORT);
    }

    @Test
    void shouldKeepSelectedSkillFilesAlignedWithRuntimeContract() throws Exception {
        for (var skillName : ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames()) {
            var skillFile = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");
            assertThat(Files.isRegularFile(skillFile))
                    .as("Missing Change Verification runtime skill: %s", skillName)
                    .isTrue();
            var content = Files.readString(skillFile);
            assertThat(content).contains("name: " + skillName);
        }

        assertThat(skill("change-verification-orchestrator")).contains(
                "RequirementLedger",
                "Readiness Gate",
                "change-verification-write-report"
        );
        assertThat(skill("change-verification-story-compliance-section")).contains(
                "STORY_COMPLIANCE",
                "interpretationType",
                "wymagan inferowanych",
                "Wymaga uwagi",
                "Potwierdzone wymagania"
        );
        assertThat(skill("change-verification-instruction-compliance-section")).contains(
                "INSTRUCTION_COMPLIANCE",
                "applicableChangedFiles",
                "interpretationType",
                "Markdown ma byc raportem dla czlowieka",
                "metadanymi pokrycia platformy",
                "nie uwzgledniaj ich przy wyznaczaniu statusu sekcji"
        );
        assertThat(skill("change-verification-write-report")).contains(
                "report_upsert_section",
                "report_update_meta",
                "report_get_current",
                "human-first Markdown",
                "## Szczegoly kryteriow",
                "Limity discovery platformy sa wylacznie `visibilityLimits`"
        );
    }

    @Test
    void shouldKeepRuntimeSkillsFeatureScopedAndFreeFromDatabaseChecks() throws Exception {
        for (var skillName : List.of(
                "change-verification-orchestrator",
                "change-verification-compliance-check",
                "change-verification-story-compliance-section",
                "change-verification-instruction-compliance-section",
                "change-verification-write-report"
        )) {
            var content = skill(skillName);
            assertThat(content).doesNotContain("features.incidentanalysis", "features.flowexplorer");
            assertThat(content).doesNotContain("C:\\", "/Users/");
        }
    }

    private String skill(String name) throws Exception {
        return Files.readString(SKILLS_ROOT.resolve(name).resolve("SKILL.md"));
    }
}
