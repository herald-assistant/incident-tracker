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
                "incident-operational-grounding",
                "incident-code-grounding",
                "incident-data-diagnostics"
        ), CopilotIncidentRuntimeSkillNames.DIAGNOSTIC_SKILL_NAMES);
        assertEquals(List.of(
                "incident-functional-analysis",
                "incident-technical-handoff"
        ), CopilotIncidentRuntimeSkillNames.RESULT_SKILL_NAMES);
        assertFalse(CopilotIncidentRuntimeSkillNames.DIAGNOSTIC_SKILL_NAMES.contains("incident-analysis-core"));
        assertFalse(CopilotIncidentRuntimeSkillNames.RESULT_SKILL_NAMES.contains("incident-analysis-core"));
        assertFalse(Files.exists(SKILLS_ROOT.resolve("incident-analysis-core")));
        assertFalse(Files.exists(SKILLS_ROOT.resolve(legacyIncidentSkill("analysis", "gitlab", "tools"))));
        assertFalse(Files.exists(SKILLS_ROOT.resolve(legacyIncidentSkill("operational", "context", "tools"))));
    }

    @Test
    void shouldKeepOrchestratorAlignedWithExpertIncidentAnalysisPlaybook() throws Exception {
        assertSkillContainsSections("incident-analysis-orchestrator", List.of(
                "## Ekspercki Model Pracy",
                "## Zasady Decyzji Orkiestratora",
                "## Granice Odpowiedzialnosci",
                "## Algorytm Orkiestracji",
                "## Readiness Gate I Petla Zwrotna",
                "## Kontrakt Orkiestracji"
        ));

        var content = Files.readString(SKILLS_ROOT
                .resolve(CopilotIncidentRuntimeSkillNames.STARTER_SKILL_NAME)
                .resolve("SKILL.md"));

        assertContainsAll(content, List.of(
                "expectedFlow: <co normalnie powinno sie wydarzyc>",
                "IncidentDiagnosisLedger",
                "CodeGroundingSummary",
                "OperationalGroundingSummary",
                "DataDiagnosticSummary",
                "observedFlow: <co evidence pokazuje w tym incydencie>",
                "divergencePoint: <pierwszy konkretny punkt rozjazdu albo Nie ustalono>",
                "incident-code-grounding",
                "incident-operational-grounding",
                "incident-data-diagnostics",
                "incident-functional-analysis",
                "incident-technical-handoff",
                "`Hypothesis tournament`",
                "`Negative evidence`",
                "`Owning-layer proof`",
                "`Information gain`",
                "`Result readiness`",
                "`Feedback loop`",
                "`Fixability test`",
                "`needs_deeper_evidence`",
                "`visibility_limited`",
                "IncidentResultReadinessFeedback",
                "minimumNextQuestion",
                "konkretnych DB checks, GitLab reads, opctx browse, sekcji Markdown ani report",
                "Finalne pola i strukture",
                "Fallback finalnej odpowiedzi nalezy do skilli wyniku",
                "omijaj skille wyniku przy finalnej odpowiedzi"
        ));
        assertFalse(content.contains("report_update_header"));
        assertFalse(content.contains("report_upsert_section"));
        assertFalse(content.contains("report_update_meta"));
        assertFalse(content.contains("report_get_current"));
        assertFalse(content.contains("Fallback JSON"));
        assertFalse(content.contains("### Macierz Diagnostyki Roznicowej"));
        assertFalse(content.contains("### Owning-Layer Proof"));
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
                "## Kontrakt Wyniku",
                "## Walidacja",
                "## Fallbacki",
                "## Artefakty Handoffu",
                "## Kiedy Wrocic Do Orkiestratora"
        );

        for (var skillName : List.of(
                "incident-code-grounding",
                "incident-data-diagnostics",
                "incident-operational-grounding"
        )) {
            assertSkillContainsSections(skillName, requiredSections);
        }

        assertContainsAll(Files.readString(SKILLS_ROOT.resolve("incident-code-grounding").resolve("SKILL.md")), List.of(
                "## Petla Code Evidence",
                "waskie poglebienie",
                "Nie finalizuj `CodeGroundingSummary` jako gotowego `technicalAnalysis`"
        ));
        assertContainsAll(Files.readString(SKILLS_ROOT.resolve("incident-data-diagnostics").resolve("SKILL.md")), List.of(
                "## Petla DB Diagnostics",
                "wykonaj jedno waskie poglebienie",
                "Nie potwierdzaj data issue bez DB evidence"
        ));
        assertContainsAll(Files.readString(SKILLS_ROOT.resolve("incident-operational-grounding").resolve("SKILL.md")), List.of(
                "## Petla Katalogowa",
                "wykonaj jedno waskie poglebienie",
                "Operational context jest groundingiem",
                "routingiem, nie dowodem root cause"
        ));
    }

    @Test
    void shouldDescribeExplicitGitLabToolScopeForIncidentAnalysis() throws Exception {
        var content = Files.readString(SKILLS_ROOT
                .resolve("incident-code-grounding")
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
                "## Wklad Do Wyniku",
                "## Kontrakt Wyniku",
                "## Readiness Gate",
                "## Walidacja",
                "## Fallbacki",
                "## Artefakty Handoffu"
        );

        for (var skillName : List.of(
                "incident-functional-analysis",
                "incident-technical-handoff"
        )) {
            assertSkillContainsSections(skillName, requiredSections);
            var content = Files.readString(SKILLS_ROOT.resolve(skillName).resolve("SKILL.md"));
            assertContainsAll(content, List.of(
                    "IncidentResultReadinessFeedback",
                    "missingArtifact",
                    "minimumNextQuestion"
            ));
        }

        var functional = Files.readString(SKILLS_ROOT.resolve("incident-functional-analysis").resolve("SKILL.md"));
        assertContainsAll(functional, List.of(
                "nie uruchamiaj tools samodzielnie",
                "functionalAnalysis"
        ));

        var technical = Files.readString(SKILLS_ROOT.resolve("incident-technical-handoff").resolve("SKILL.md"));
        assertContainsAll(technical, List.of(
                "W initial analysis",
                "W follow-upie moze dociagnac tylko brakujacy, konkretny szczegol",
                "uruchamiaj wtedy samodzielnie tools",
                "nie tworz",
                "pozornie kompletnego handoffu"
        ));
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

    private static String legacyIncidentSkill(String... parts) {
        return "incident-" + String.join("-", parts);
    }
}
