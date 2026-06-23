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
        assertEquals(
                "flow-explorer-goal-deep-discovery",
                FlowExplorerCopilotRuntimeSkillNames.DEEP_DISCOVERY_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-goal-test-scenarios",
                FlowExplorerCopilotRuntimeSkillNames.TEST_SCENARIOS_SKILL_NAME
        );
        assertEquals(
                "flow-explorer-goal-risk-detection",
                FlowExplorerCopilotRuntimeSkillNames.RISK_DETECTION_SKILL_NAME
        );
        assertEquals(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-gitlab-tools",
                "flow-explorer-operational-context-tools",
                "flow-explorer-result-contract",
                "flow-explorer-goal-deep-discovery",
                "flow-explorer-goal-test-scenarios",
                "flow-explorer-goal-risk-detection"
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
                "## Sterowanie Analiza",
                "## Algorytm Pracy",
                "## Zasady Kosztowe",
                "## Kiedy Uzyc Tools",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-gitlab-tools", List.of(
                "## Rola Wobec Orkiestratora",
                "## Dozwolone Tools",
                "## Strategia Czytania Kodu",
                "## Szybka Strategia Dla Slabszych Modeli",
                "## Co Czytac Wedlug Section Modes",
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
                "## Sekcje I Kolejnosc",
                "## Jezyk I Odbiorca",
                "## Source References",
                "## Confidence I Visibility Limits",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-deep-discovery", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Business flow/rules",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Compact Vs Deep",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-test-scenarios", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Business flow/rules",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Format Sekcji",
                "## Antywzorce"
        ));
        assertSkillContainsSections("flow-explorer-goal-risk-detection", List.of(
                "## Cel",
                "## Zasada Ogolna",
                "## Overview",
                "## Business flow/rules",
                "## Validations",
                "## Persistence",
                "## Integrations",
                "## Format Sekcji",
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
        var deepDiscovery = Files.readString(SKILLS_ROOT.resolve("flow-explorer-goal-deep-discovery").resolve("SKILL.md"));
        var resultContract = Files.readString(SKILLS_ROOT.resolve("flow-explorer-result-contract").resolve("SKILL.md"));
        var operationalContextTools = Files.readString(
                SKILLS_ROOT.resolve("flow-explorer-operational-context-tools").resolve("SKILL.md"));

        assertTrue(orchestrator.contains("`branchRef`"));
        assertTrue(orchestrator.contains("`applicationName`"));
        assertTrue(orchestrator.contains("`flow-explorer/canonical-tool-inputs.md`"));
        assertTrue(orchestrator.contains("`compact-flow-manifest.md` jest kanoniczna lista"));
        assertTrue(orchestrator.contains("Nie zgaduj `projectName`, `projectPath`"));
        assertTrue(orchestrator.contains("Hidden `ToolContext` jest tylko techniczna mechanika runtime"));
        assertTrue(orchestrator.contains("Nie przekazuj `gitLabGroup` do tools"));
        assertTrue(orchestrator.contains("`sectionModes` sa zrodlem prawdy dla sekcji wyniku"));
        assertTrue(orchestrator.contains("`OFF` oznacza: nie zwracaj tej sekcji w `sections`"));
        assertTrue(orchestrator.contains("`focusAreas` nie sa celem"));
        assertTrue(orchestrator.contains("`reasoningEffort` okresla glebokosc eksploracji"));

        assertTrue(gitLabTools.contains("GitLab tools nie czytaja business scope'u z hidden `ToolContext`"));
        assertTrue(gitLabTools.contains("`flow-explorer/canonical-tool-inputs.md`"));
        assertTrue(gitLabTools.contains("`filePath` i `methodSelectors` z `flow-explorer/compact-flow-manifest.md`"));
        assertTrue(gitLabTools.contains("Nie zaczynaj od `gitlab_read_repository_file`"));
        assertTrue(gitLabTools.contains("`truncated=true`, traktuj go jako prefix"));
        assertTrue(gitLabTools.contains("korzystajac z `totalLines`, `returnedStartLine` i"));
        assertTrue(gitLabTools.contains("`branchRef`"));
        assertTrue(gitLabTools.contains("`applicationName`"));
        assertTrue(gitLabTools.contains("Nie uzywaj `gitlab_list_available_repositories`"));
        assertTrue(gitLabTools.contains("Nie przekazuj `gitLabGroup`"));
        assertTrue(gitLabTools.contains("`sectionModes` wskazuje, ktore obszary maja byc zwrocone"));
        assertTrue(gitLabTools.contains("`OFF` oznacza, ze nie czytaj dodatkowego kodu tylko dla tej sekcji"));
        assertTrue(gitLabTools.contains("Glebokoscia nadal steruje"));
        assertTrue(gitLabTools.contains("`typeSummaries`, `fieldSummaries`"));
        assertTrue(gitLabTools.contains("Adnotacje sa przypiete do"));
        assertTrue(gitLabTools.contains("`TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS`"));
        assertTrue(gitLabTools.contains("Ustal `SOURCE` dla kazdej zapisywanej"));
        assertTrue(gitLabTools.contains("Nie koncz persistence deep na polach encji"));
        assertTrue(gitLabTools.contains("nazwac `SOURCE` biznesowo"));
        assertFalse(gitLabTools.contains("`gitLabGroup` i `gitLabBranch` pochodza z hidden ToolContext"));

        assertTrue(deepDiscovery.contains("| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |"));
        assertTrue(deepDiscovery.contains("`SOURCE` jest obowiazkowe"));
        assertTrue(deepDiscovery.contains("`GENERATED`"));
        assertTrue(deepDiscovery.contains("`REQUEST`"));
        assertTrue(deepDiscovery.contains("`CALCULATED`"));
        assertTrue(deepDiscovery.contains("biznesowa nazwa systemu albo komponentu"));
        assertTrue(deepDiscovery.contains("Nie koncz `PERSISTENCE=DEEP` bez ustalenia `SOURCE`"));
        assertTrue(deepDiscovery.contains("szczegoly implementacyjne nie sa trescia tabeli wynikowej"));

        assertTrue(resultContract.contains("## Persistence Deep Contract"));
        assertTrue(resultContract.contains("`SOURCE` jest polem kontrolowanym"));
        assertTrue(resultContract.contains("Dozwolone wartosci to tylko"));
        assertTrue(resultContract.contains("Nie wpisuj w `SOURCE` ani `SOURCE DETAILS` nazw klas"));

        assertTrue(operationalContextTools.contains("### SOURCE Dla Persistence Deep"));
        assertTrue(operationalContextTools.contains("nazwac `SOURCE` biznesowo"));
        assertTrue(operationalContextTools.contains("Nie wpisuj jako `SOURCE` nazw"));
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
