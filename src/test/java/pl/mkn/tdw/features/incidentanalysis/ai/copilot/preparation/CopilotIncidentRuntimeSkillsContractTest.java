package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentRuntimeSkillsContractTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldUseOrchestratorAsStarterSkillWithoutLegacyCoreSkill() {
        assertEquals("incident-analysis-orchestrator", CopilotIncidentRuntimeSkillNames.STARTER_SKILL_NAME);
        assertEquals(List.of(
                "incident-operational-context-tools",
                "incident-analysis-gitlab-tools",
                "incident-data-diagnostics"
        ), CopilotIncidentRuntimeSkillNames.DIAGNOSTIC_SKILL_NAMES);
        assertEquals(List.of(
                "incident-functional-analysis",
                "incident-technical-handoff"
        ), CopilotIncidentRuntimeSkillNames.RESULT_SKILL_NAMES);
        assertFalse(CopilotIncidentRuntimeSkillNames.DIAGNOSTIC_SKILL_NAMES.contains("incident-analysis-core"));
        assertFalse(CopilotIncidentRuntimeSkillNames.RESULT_SKILL_NAMES.contains("incident-analysis-core"));
        assertFalse(Files.exists(SKILLS_ROOT.resolve("incident-analysis-core")));
    }

    @Test
    void shouldKeepOrchestratorAlignedWithExpertIncidentAnalysisPlaybook() throws Exception {
        assertSkillContainsSections("incident-analysis-orchestrator", List.of(
                "## Ekspercki Model Pracy",
                "### Zasady Trafnosci Eksperta",
                "### Owning-Layer Proof",
                "### Macierz Diagnostyki Roznicowej"
        ));

        var content = Files.readString(SKILLS_ROOT
                .resolve(CopilotIncidentRuntimeSkillNames.STARTER_SKILL_NAME)
                .resolve("SKILL.md"));

        assertContainsAll(content, List.of(
                "expectedFlow: co normalnie powinno sie wydarzyc",
                "observedFlow: co evidence pokazuje w tym incydencie",
                "divergencePoint: pierwszy konkretny punkt rozjazdu",
                "Hypothesis tournament",
                "Negative evidence",
                "Owning-layer proof",
                "Information gain",
                "Fixability test",
                "zrodlem prawdy initial analysis jest raport zapisany przez report tools",
                "`report_update_header`",
                "`report_upsert_section` z `id=FUNCTIONAL_ANALYSIS`",
                "`report_upsert_section` z `id=TECHNICAL_HANDOFF`",
                "`report_update_meta`",
                "`report_get_current`",
                "Nie przekazuj `reportId` do report tools",
                "repository empty result",
                "key-only = 0 -> `data_missing`",
                "key-only > 0 i full = 0 -> `data_predicate_mismatch`",
                "predicate w kodzie bledny -> `code_query_or_repository_logic`",
                "Jezeli test obala aktualna klase, przeklasyfikuj"
        ));
    }

    @Test
    void shouldKeepDiagnosticSkillsAlignedWithOrchestratorContract() throws Exception {
        var requiredSections = List.of(
                "## Rola Wobec Orkiestratora",
                "## Wejscie Oczekiwane Od Orkiestratora",
                "## Klasy Bledu Obslugiwane Przez Ten Skill",
                "## Hipotezy, Ktore Skill Ma Potwierdzic Albo Obalic",
                "## Testy Rozrozniajace",
                "## Wklad Do Wyniku",
                "## Kiedy Wrocic Do Orkiestratora"
        );

        for (var skillName : List.of(
                "incident-analysis-gitlab-tools",
                "incident-data-diagnostics",
                "incident-operational-context-tools"
        )) {
            assertSkillContainsSections(skillName, requiredSections);
        }
    }

    @Test
    void shouldDescribeExplicitGitLabToolScopeForIncidentAnalysis() throws Exception {
        var content = Files.readString(SKILLS_ROOT
                .resolve("incident-analysis-gitlab-tools")
                .resolve("SKILL.md"));

        assertContainsAll(content, List.of(
                "`branchRef`",
                "`projectName`",
                "`applicationName`",
                "Nie przekazuj `gitLabGroup` do GitLab tools",
                "Backend rozstrzyga GitLab group",
                "`gitlab_build_java_method_use_case_context`",
                "`maxResults`",
                "`gitlab_read_java_method_slice`"
        ));
        assertContainsAll(content, List.of(
                "JPA/repository/data symptoms",
                "## Heurystyki Java/Spring",
                "Liquibase, Flyway albo DDL traktuj jako fallback",
                "Feign clients, RestClient, WebClient albo RestTemplate",
                "`StreamBridge`",
                "`Consumer`, `Function` albo `Supplier`"
        ));
        assertFalse(content.contains("stalej session group"));
        assertFalse(content.contains("hidden ToolContext"));
        assertFalse(content.contains("focusHints"));
        assertFalse(content.contains("maxFiles"));
    }

    @Test
    void shouldKeepResultContractSkillsAlignedWithOrchestratorContract() throws Exception {
        var requiredSections = List.of(
                "## Rola Wobec Orkiestratora",
                "## Wejscie Oczekiwane Od Orkiestratora",
                "## Czego Ten Skill Nie Diagnozuje",
                "## Wklad Do Wyniku"
        );

        for (var skillName : List.of(
                "incident-functional-analysis",
                "incident-technical-handoff"
        )) {
            assertSkillContainsSections(skillName, requiredSections);
        }
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

    private static void assertContainsAll(String content, List<String> expectedFragments) {
        for (var expectedFragment : expectedFragments) {
            assertTrue(content.contains(expectedFragment), () -> "Missing expected fragment: " + expectedFragment);
        }
    }
}
