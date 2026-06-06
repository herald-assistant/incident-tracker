package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentDataMissingFixtureTest {

    private static final String FIXTURE_RESOURCE = "copilot/incident-analysis-fixtures/data-missing.md";
    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldDefineDataMissingDryRunRoutingContract() throws Exception {
        var fixture = readResource(FIXTURE_RESOURCE);

        assertContainsAll(fixture, List.of(
                "expectedClassification: data_missing",
                "starterSkill: incident-analysis-orchestrator",
                "specializedSkill: incident-data-diagnostics",
                "Najpierw research flow przez `incident-analysis-orchestrator`",
                "Potem klasyfikacja jako `data_missing`",
                "Potem przejscie do `incident-data-diagnostics`",
                "`HTTP request -> controller -> service -> repository lookup -> missing row`",
                "key-only check",
                "full-predicate check",
                "Jesli key-only count = `0`, utrzymaj `data_missing`",
                "Jesli key-only count > `0`, ale full-predicate count = `0`, zmien klase na",
                "`data_predicate_mismatch`",
                "`functionalAnalysis`",
                "`technicalAnalysis`"
        ));
    }

    @Test
    void shouldKeepRuntimeSkillsAbleToExecuteDataMissingFixture() throws Exception {
        var orchestrator = readSkill("incident-analysis-orchestrator");
        var dataDiagnostics = readSkill("incident-data-diagnostics");

        var flowResearchIndex = orchestrator.indexOf("### 3. Zbadaj Flow Dotknietego Use Case'u");
        var classificationIndex = orchestrator.indexOf("### 5. Sklasyfikuj Typ Bledu");

        assertTrue(flowResearchIndex >= 0, "Orchestrator should describe flow research");
        assertTrue(classificationIndex >= 0, "Orchestrator should describe error classification");
        assertTrue(flowResearchIndex < classificationIndex, "Orchestrator should research affected flow first");
        assertContainsAll(orchestrator, List.of(
                "`data_missing`",
                "`data_predicate_mismatch`",
                "Uzyj `incident-data-diagnostics` dla missing data",
                "`functionalAnalysis`: Functional Analysis v1",
                "`technicalAnalysis`: Technical Handoff v1"
        ));
        assertContainsAll(dataDiagnostics, List.of(
                "`data_missing`",
                "`data_predicate_mismatch`",
                "key-only count = 0",
                "full-predicate count = 0",
                "Nie wolno oglosic data issue",
                "DB evidence",
                "`functionalAnalysis`",
                "`technicalAnalysis`"
        ));
    }

    private static String readSkill(String skillName) throws IOException {
        return Files.readString(SKILLS_ROOT.resolve(skillName).resolve("SKILL.md"));
    }

    private static String readResource(String resourceName) throws IOException {
        try (var inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            assertNotNull(inputStream, () -> "Missing test resource: " + resourceName);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertContainsAll(String content, List<String> expectedFragments) {
        for (var expectedFragment : expectedFragments) {
            assertTrue(
                    content.contains(expectedFragment),
                    () -> "Missing expected fragment: " + expectedFragment
            );
        }
    }
}
