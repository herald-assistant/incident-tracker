package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerCopilotRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDeclareExpectedFlowExplorerRuntimeSkills() {
        assertEquals("flow-explorer-orchestrator", FlowExplorerCopilotRuntimeSkillNames.STARTER_SKILL_NAME);
        assertEquals("flow-explorer-gitlab-tools", FlowExplorerCopilotRuntimeSkillNames.GITLAB_TOOLS_SKILL_NAME);
        assertEquals(
                "flow-explorer-operational-context-tools",
                FlowExplorerCopilotRuntimeSkillNames.OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-result-contract",
                FlowExplorerCopilotRuntimeSkillNames.RESULT_CONTRACT_SKILL_NAME
        );
        assertEquals(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-gitlab-tools",
                "flow-explorer-operational-context-tools",
                "flow-explorer-result-contract"
        ), FlowExplorerCopilotRuntimeSkillNames.allSkillNames());
        assertEquals(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-gitlab-tools",
                "flow-explorer-operational-context-tools"
        ), FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames());
    }

    @Test
    void shouldKeepFlowExplorerSkillFilesAlignedWithRuntimeContract() throws Exception {
        assertSkillContainsSections("flow-explorer-orchestrator", List.of(
                "## Rola",
                "## Wejscie Sesji",
                "## Algorytm Pracy",
                "## Zasady Kosztowe",
                "## Kiedy Uzyc Tools",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-gitlab-tools", List.of(
                "## Rola Wobec Orkiestratora",
                "## Dozwolone Tools",
                "## Strategia Czytania Kodu",
                "## Co Czytac Wedlug Focus Area",
                "## Zasady Kosztowe",
                "## Wklad Do Wyniku"
        ));
        assertSkillContainsSections("flow-explorer-operational-context-tools", List.of(
                "## Rola Wobec Orkiestratora",
                "## Dozwolone Tools",
                "## Kiedy Uzyc",
                "## Zasady Interpretacji",
                "## Wklad Do Wyniku"
        ));
        assertSkillContainsSections("flow-explorer-result-contract", List.of(
                "## Rola",
                "## Wymagany JSON Contract",
                "## Jezyk I Odbiorca",
                "## Source References",
                "## Confidence I Visibility Limits",
                "## Antywzorce"
        ));
    }

    @Test
    void shouldKeepFlowExplorerSkillsFeatureScopedAndFreeFromLocalSecrets() throws Exception {
        for (var skillName : FlowExplorerCopilotRuntimeSkillNames.allSkillNames()) {
            var content = Files.readString(SKILLS_ROOT.resolve(skillName).resolve("SKILL.md"));

            assertFalse(content.contains("incident-analysis"), () -> "Incident skill reference leaked into " + skillName);
            assertFalse(content.contains("C:\\"), () -> "Local Windows path leaked into " + skillName);
            assertFalse(content.contains("/Users/"), () -> "Local Unix path leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("githubtoken"), () -> "Token hint leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("password"), () -> "Password hint leaked into " + skillName);
        }
    }

    @Test
    void shouldDescribeExplicitGitLabToolScopeForFlowExplorer() throws Exception {
        var orchestrator = Files.readString(SKILLS_ROOT.resolve("flow-explorer-orchestrator").resolve("SKILL.md"));
        var gitLabTools = Files.readString(SKILLS_ROOT.resolve("flow-explorer-gitlab-tools").resolve("SKILL.md"));

        assertTrue(orchestrator.contains("`branchRef`"));
        assertTrue(orchestrator.contains("`applicationName`"));
        assertTrue(orchestrator.contains("Hidden `ToolContext` jest tylko techniczna mechanika runtime"));
        assertTrue(orchestrator.contains("Nie przekazuj `gitLabGroup` do tools"));

        assertTrue(gitLabTools.contains("GitLab tools nie czytaja business scope'u z hidden `ToolContext`"));
        assertTrue(gitLabTools.contains("`branchRef`"));
        assertTrue(gitLabTools.contains("`applicationName`"));
        assertTrue(gitLabTools.contains("Nie przekazuj `gitLabGroup`"));
        assertFalse(gitLabTools.contains("`gitLabGroup` i `gitLabBranch` pochodza z hidden ToolContext"));
    }

    private static void assertSkillContainsSections(String skillName, List<String> requiredSections) throws Exception {
        var skillFile = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");

        assertTrue(Files.exists(skillFile), () -> "Missing runtime skill: " + skillName);

        var content = Files.readString(skillFile);

        assertTrue(content.contains("name: " + skillName), () -> "Missing frontmatter name for " + skillName);
        for (var section : requiredSections) {
            assertTrue(content.contains(section), () -> "Missing section '" + section + "' in " + skillName);
        }
    }
}
