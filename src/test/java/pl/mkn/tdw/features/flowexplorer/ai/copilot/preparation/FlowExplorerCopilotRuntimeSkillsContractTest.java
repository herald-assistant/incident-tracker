package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;

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
        assertEquals("flow-explorer-code-grounding", FlowExplorerCopilotRuntimeSkillNames.CODE_GROUNDING_SKILL_NAME);
        assertEquals("flow-explorer-operational-grounding",
                FlowExplorerCopilotRuntimeSkillNames.OPERATIONAL_GROUNDING_SKILL_NAME);
        assertEquals("flow-explorer-map-persistence-section",
                FlowExplorerCopilotRuntimeSkillNames.PERSISTENCE_SECTION_SKILL_NAME);
        assertEquals("flow-explorer-map-integrations-section",
                FlowExplorerCopilotRuntimeSkillNames.INTEGRATIONS_SECTION_SKILL_NAME);
        assertEquals("flow-explorer-write-report", FlowExplorerCopilotRuntimeSkillNames.WRITE_REPORT_SKILL_NAME);
        assertEquals("flow-explorer-follow-up-chat", FlowExplorerCopilotRuntimeSkillNames.FOLLOW_UP_CHAT_SKILL_NAME);
        assertEquals("flow-explorer-deep-discovery", FlowExplorerCopilotRuntimeSkillNames.DEEP_DISCOVERY_SKILL_NAME);
        assertEquals("flow-explorer-test-scenario-design",
                FlowExplorerCopilotRuntimeSkillNames.TEST_SCENARIOS_SKILL_NAME);
        assertEquals("flow-explorer-risk-assessment", FlowExplorerCopilotRuntimeSkillNames.RISK_DETECTION_SKILL_NAME);

        assertEquals(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-code-grounding",
                "flow-explorer-operational-grounding",
                "flow-explorer-map-persistence-section",
                "flow-explorer-map-integrations-section",
                "flow-explorer-write-report",
                "flow-explorer-follow-up-chat",
                "flow-explorer-deep-discovery",
                "flow-explorer-test-scenario-design",
                "flow-explorer-risk-assessment"
        ), FlowExplorerCopilotRuntimeSkillNames.allSkillNames());
        assertEquals(List.of(
                "flow-explorer-follow-up-chat",
                "flow-explorer-code-grounding",
                "flow-explorer-operational-grounding",
                "flow-explorer-map-persistence-section",
                "flow-explorer-map-integrations-section"
        ), FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames());
    }

    @Test
    void shouldExposeSectionSkillsForInitialSessionsWhenSectionsAreActive() {
        for (var goal : FlowExplorerAnalysisGoal.values()) {
            var fallbackSkillNames = FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(goal);

            assertTrue(fallbackSkillNames.contains(FlowExplorerCopilotRuntimeSkillNames.PERSISTENCE_SECTION_SKILL_NAME));
            assertTrue(fallbackSkillNames.contains(FlowExplorerCopilotRuntimeSkillNames.INTEGRATIONS_SECTION_SKILL_NAME));
            assertTrue(fallbackSkillNames.contains(FlowExplorerCopilotRuntimeSkillNames.WRITE_REPORT_SKILL_NAME));
        }

        var persistenceOnly = FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(
                        sectionMode(FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.COMPACT),
                        sectionMode(FlowExplorerResultSectionId.INTEGRATIONS, FlowExplorerResultSectionMode.OFF)
                )
        );
        assertTrue(persistenceOnly.contains(FlowExplorerCopilotRuntimeSkillNames.PERSISTENCE_SECTION_SKILL_NAME));
        assertFalse(persistenceOnly.contains(FlowExplorerCopilotRuntimeSkillNames.INTEGRATIONS_SECTION_SKILL_NAME));

        var integrationsOnly = FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames(List.of(
                sectionMode(FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.OFF),
                sectionMode(FlowExplorerResultSectionId.INTEGRATIONS, FlowExplorerResultSectionMode.DEEP)
        ));
        assertFalse(integrationsOnly.contains(FlowExplorerCopilotRuntimeSkillNames.PERSISTENCE_SECTION_SKILL_NAME));
        assertTrue(integrationsOnly.contains(FlowExplorerCopilotRuntimeSkillNames.INTEGRATIONS_SECTION_SKILL_NAME));
    }

    private static FlowExplorerResultSectionModeAssignment sectionMode(
            FlowExplorerResultSectionId id,
            FlowExplorerResultSectionMode mode
    ) {
        return new FlowExplorerResultSectionModeAssignment(id, id.title(), mode);
    }

    @Test
    void shouldKeepFlowExplorerSkillFilesAlignedWithRuntimeContract() throws Exception {
        for (var skillName : FlowExplorerCopilotRuntimeSkillNames.allSkillNames()) {
            assertSkillContainsCoreSections(skillName);
        }

        assertSkillContainsAny("flow-explorer-orchestrator", List.of("## Wejscia"));
        assertSkillContainsAny("flow-explorer-orchestrator", List.of("## Algorytm Pracy"));
        assertSkillContainsAny("flow-explorer-code-grounding", List.of("## Scope Tooli"));
        assertSkillContainsAny("flow-explorer-write-report", List.of("## Wymagany Report Contract"));
        assertSkillContainsAny("flow-explorer-follow-up-chat", List.of("## Format Odpowiedzi"));
    }

    @Test
    void shouldRemoveLegacyFlowExplorerSkillDirectories() {
        for (var legacySkillName : List.of(
                legacyFlowSkill("gitlab", "tools"),
                legacyFlowSkill("operational-context", "tools"),
                legacyFlowSkill("result", "contract"),
                legacyFlowSkill("goal", "deep-discovery"),
                legacyFlowSkill("goal", "test-scenarios"),
                legacyFlowSkill("goal", "risk-detection"),
                legacyFlowSkill("map", "persistence-deep"),
                legacyFlowSkill("map", "integration-boundaries")
        )) {
            assertFalse(Files.exists(SKILLS_ROOT.resolve(legacySkillName)),
                    () -> "Legacy runtime skill directory should not exist: " + legacySkillName);
        }
    }

    @Test
    void shouldKeepFlowExplorerSkillsFeatureScopedAndFreeFromLocalSecrets() throws Exception {
        for (var skillName : FlowExplorerCopilotRuntimeSkillNames.allSkillNames()) {
            var content = skillContent(skillName);

            assertFalse(content.contains("incident-analysis"), () -> "Incident skill reference leaked into " + skillName);
            assertFalse(content.contains("C:\\"), () -> "Local Windows path leaked into " + skillName);
            assertFalse(content.contains("/Users/"), () -> "Local Unix path leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("githubtoken"), () -> "Token hint leaked into " + skillName);
            assertFalse(content.toLowerCase().contains("password"), () -> "Password hint leaked into " + skillName);
        }
    }

    @Test
    void shouldSplitCodeGroundingPersistenceMappingIntegrationMappingAndReportWriting() throws Exception {
        var orchestrator = skillContent("flow-explorer-orchestrator");
        var codeGrounding = skillContent("flow-explorer-code-grounding");
        var persistenceMapping = skillContent("flow-explorer-map-persistence-section");
        var integrationMapping = skillContent("flow-explorer-map-integrations-section");
        var writeReport = skillContent("flow-explorer-write-report");
        var deepDiscovery = skillContent("flow-explorer-deep-discovery");
        var testScenarioDesign = skillContent("flow-explorer-test-scenario-design");
        var riskAssessment = skillContent("flow-explorer-risk-assessment");
        var followUpChat = skillContent("flow-explorer-follow-up-chat");

        assertContainsAll(orchestrator, List.of(
                "PersistenceMappingSummary",
                "IntegrationBoundarySummary",
                "flow-explorer-map-persistence-section",
                "flow-explorer-map-integrations-section",
                "flow-explorer-write-report",
                "## Zasady Decyzji Orkiestratora",
                "`Information gain`",
                "`Result readiness`",
                "Readiness Gate I Petla Zwrotna",
                "`needs_deeper_evidence`",
                "ReportReadinessFeedback",
                "## Kontrakt Orkiestracji",
                "Finalny kontrakt nalezy do",
                "nie opisuje formatu raportu"
        ));
        assertFalse(orchestrator.contains("report_upsert_section"));
        assertFalse(orchestrator.contains("report_get_current"));
        assertFalse(orchestrator.contains("Fallback JSON"));
        assertFalse(orchestrator.contains("sekcje `OFF`"));
        assertContainsAll(codeGrounding, List.of(
                "CodeGroundingSummary",
                "Focused code grounding",
                "Dostarcza tylko code evidence",
                "`flow-explorer-map-persistence-section`",
                "`flow-explorer-map-integrations-section`",
                "nie domykaj tabel, kolumn, `SOURCE`",
                "## Petla Evidence",
                "jedno waskie poglebienie"
        ));
        assertFalse(codeGrounding.contains("TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS"));
        assertFalse(codeGrounding.contains("exhaustive persistence table closure"));
        assertFalse(codeGrounding.contains("Dla `DEEP` domknij kontrakt: path/destination, payload"));
        assertFalse(codeGrounding.contains("Pelny kontrakt granic"));

        assertContainsAll(persistenceMapping, List.of(
                "PersistenceMappingSummary",
                "`PERSISTENCE` jest aktywne w trybie `COMPACT` albo `DEEP`",
                "| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |",
                "Nie uzywaj DDL, Liquibase, Flyway",
                "join tables i kolekcje maja domkniete tabele elementow",
                "## Petla Poglebiania",
                "Nie finalizuj wyniku `DEEP` jako `COMPACT`"
        ));
        assertContainsAll(integrationMapping, List.of(
                "IntegrationBoundarySummary",
                "`sectionModes.INTEGRATIONS` ma tryb `COMPACT` albo `DEEP`",
                "| System/target | Typ | Adres/kanal/path | Moment w flow |",
                "path/destination, payload albo jawny visibility limit",
                "rzeczywista granica zewnetrzna",
                "## Petla Poglebiania",
                "Nie finalizuj wyniku `DEEP` jako `COMPACT`"
        ));
        assertContainsAll(writeReport, List.of(
                "AnalysisReport",
                "Persistence Mapping Input",
                "Integration Boundary Input",
                "report_get_current",
                "## Readiness Gate",
                "ReportReadinessFeedback",
                "zapisuj czesciowego raportu"
        ));
        assertFalse(writeReport.contains("## Persistence Deep Contract"));
        assertFalse(writeReport.contains("## Integration Boundary Contract"));

        for (var goalSkill : List.of(deepDiscovery, testScenarioDesign, riskAssessment)) {
            assertTrue(goalSkill.contains("GoalGuidance"));
            assertTrue(goalSkill.contains("PersistenceMappingSummary"));
            assertTrue(goalSkill.contains("IntegrationBoundarySummary"));
            assertTrue(goalSkill.contains("soczewka celu"));
            assertFalse(goalSkill.contains("exhaustive persistence table closure"));
            assertFalse(goalSkill.contains("Nie traktuj `maxDepth reached`"));
            assertFalse(goalSkill.contains("## Functional flow"));
            assertFalse(goalSkill.contains("## Validations"));
            assertFalse(goalSkill.contains("### compact"));
            assertFalse(goalSkill.contains("### deep"));
            assertFalse(goalSkill.contains("| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |"));
            assertFalse(goalSkill.contains("path/destination, payload"));
        }

        assertContainsAll(followUpChat, List.of(
                "flow-explorer-map-persistence-section",
                "flow-explorer-map-integrations-section"
        ));
    }

    @Test
    void shouldDescribeExplicitGitLabAndOperationalToolScopeForFlowExplorer() throws Exception {
        var orchestrator = skillContent("flow-explorer-orchestrator");
        var codeGrounding = skillContent("flow-explorer-code-grounding");
        var operationalGrounding = skillContent("flow-explorer-operational-grounding");

        assertContainsAll(orchestrator, List.of(
                "`flow-explorer/canonical-tool-inputs.md`",
                "Nie przekazuj `gitLabGroup` do tools",
                "domyslny discovery scope",
                "explicit focused read",
                "`sectionModes` sluzy orkiestratorowi tylko do decyzji",
                "`reasoningEffort` ogranicza glebokosc orkiestracji",
                "Hidden `ToolContext` jest techniczna mechanika runtime",
                "`Goal alignment`"
        ));
        assertFalse(orchestrator.contains("report_upsert_section"));
        assertFalse(orchestrator.contains("`sectionModes` sa zrodlem prawdy dla sekcji wyniku"));
        assertContainsAll(codeGrounding, List.of(
                "GitLab tools nie czytaja functional scope'u z hidden `ToolContext`",
                "`branchRef`, `applicationName` i `projectName`",
                "`filePath` i `methodSelectors`",
                "`searchMode/pathPrefixes` sa domyslnym discovery scope",
                "poza domyslny discovery scope",
                "Nie zaczynaj od `gitlab_read_repository_file`",
                "Po `truncated=true` nie wnioskuj o dalszej czesci pliku",
                "Nie przekazuj `gitLabGroup`"
        ));
        assertContainsAll(operationalGrounding, List.of(
                "OperationalGroundingSummary",
                "Operational context nie jest dowodem",
                "nie blokada dla focused read",
                "## Petla Katalogowa",
                "jedno waskie",
                "biznesowa nazwe systemu",
                "Nie wpisuj jako `SOURCE` nazw",
                "nie formatuj sekcji `PERSISTENCE`"
        ));
    }

    private static void assertSkillContainsCoreSections(String skillName) throws Exception {
        var content = skillContent(skillName);

        assertTrue(content.contains("name: " + skillName), () -> "Missing frontmatter name for " + skillName);
        assertContainsAll(content, List.of(
                "## Cel",
                "## Walidacja",
                "## Fallbacki",
                "## Artefakty Handoffu"
        ));
        if ("flow-explorer-orchestrator".equals(skillName)) {
            assertTrue(content.contains("## Kontrakt Orkiestracji"),
                    () -> "Missing orchestration contract in " + skillName);
            assertFalse(content.contains("## Kontrakt Wyniku"),
                    () -> "Orchestrator must not own final result contract");
        } else {
            assertTrue(content.contains("## Kontrakt Wyniku"),
                    () -> "Missing result contract in " + skillName);
        }
        assertSkillContainsAny(skillName, List.of("## Wejscia", "## Wejscie Sesji"));
        assertSkillContainsAny(skillName, List.of("## Procedura", "## Algorytm", "## Algorytm Pracy"));
    }

    private static void assertSkillContainsAny(String skillName, List<String> expectedSections) throws Exception {
        var content = skillContent(skillName);
        assertTrue(expectedSections.stream().anyMatch(content::contains),
                () -> "Missing one of sections " + expectedSections + " in " + skillName);
    }

    private static String skillContent(String skillName) throws Exception {
        var skillFile = SKILLS_ROOT.resolve(skillName).resolve("SKILL.md");
        assertTrue(Files.exists(skillFile), () -> "Missing runtime skill: " + skillName);
        return Files.readString(skillFile);
    }

    private static String legacyFlowSkill(String middle, String suffix) {
        return "flow-explorer-" + middle + "-" + suffix;
    }

    private static void assertContainsAll(String content, List<String> expectedFragments) {
        for (var expectedFragment : expectedFragments) {
            assertTrue(content.contains(expectedFragment), () -> "Missing expected fragment: " + expectedFragment);
        }
    }
}
