package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiExplorerCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldProvideThreePolishUiExplorerSkillsWithRequiredContracts() throws Exception {
        for (var skillName : UiExplorerCopilotRuntimeSkillNames.featureSkillNames()) {
            var content = skill(skillName);
            assertThat(content).startsWith("---\nname: " + skillName + "\n");
            assertThat(content).contains(
                    "## Cel",
                    "## Wejscia",
                    "## Output Contract",
                    "## Walidacja",
                    "## Fallbacki",
                    "## Artefakty Handoffu"
            );
            assertThat(content)
                    .doesNotContain("incident-analysis")
                    .doesNotContain("flow-explorer")
                    .doesNotContain("C:\\")
                    .doesNotContain("/Users/");
        }
    }

    @Test
    void shouldKeepOrchestrationGroundingAndResultOwnershipSeparated() throws Exception {
        var orchestrator = skill(UiExplorerCopilotRuntimeSkillNames.ORCHESTRATOR);
        var grounding = skill(UiExplorerCopilotRuntimeSkillNames.SOURCE_GROUNDING);
        var writer = skill(UiExplorerCopilotRuntimeSkillNames.WRITE_REPORT);

        assertThat(orchestrator)
                .contains("readiness")
                .contains("completenessSignals")
                .contains("ui-explorer-source-grounding")
                .contains("ui-explorer-write-report")
                .doesNotContain("\"functionalOverview\"");
        assertThat(grounding)
                .contains("UNTRUSTED_SOURCE_EVIDENCE")
                .contains("ignore previous instructions")
                .contains("completenessSignals")
                .contains("SourceGroundingSummary")
                .doesNotContain("Finalnym artefaktem jest jeden JSON");
        assertThat(writer)
                .contains("Zrodlem prawdy jest `AnalysisReport` zapisany przez report tools")
                .contains("FORMS_AND_RULES")
                .contains("completenessSignals")
                .contains("report_update_header")
                .contains("report_upsert_section")
                .contains("report_update_meta")
                .contains("report_get_current")
                .contains("Nie zwracaj JSON");
    }

    @Test
    void shouldExposeCanonicalSkillNamesWithoutAliases() {
        assertThat(UiExplorerCopilotRuntimeSkillNames.featureSkillNames())
                .containsExactly(
                        "ui-explorer-orchestrator",
                        "ui-explorer-source-grounding",
                        "ui-explorer-write-report"
                );
        assertThat(List.of(
                "ui-explorer-analysis",
                "ui-explorer-code-analysis",
                "ui-explorer-report"
        )).allSatisfy(alias -> assertThat(SKILLS_ROOT.resolve(alias)).doesNotExist());
    }

    private String skill(String skillName) throws Exception {
        var path = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");
        assertThat(path).isRegularFile();
        return Files.readString(path).replace("\r\n", "\n");
    }
}
