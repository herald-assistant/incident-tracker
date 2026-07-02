package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentAnalysisFixturesTest {

    private static final Path FIXTURE_ROOT = Path.of(
            "src",
            "test",
            "resources",
            "copilot",
            "incident-analysis-fixtures"
    );

    private static final List<String> EXPECTED_CLASSIFICATIONS = List.of(
            "data_missing",
            "data_predicate_mismatch",
            "data_orphan_or_stale_reference",
            "data_duplicate_or_non_unique",
            "code_mapping_or_type_conversion",
            "code_query_or_repository_logic",
            "code_validation_or_business_rule",
            "integration_downstream_failure",
            "async_or_process_state",
            "runtime_or_platform",
            "configuration_or_environment",
            "outside_visibility_or_handoff",
            "inconclusive"
    );

    private static final List<String> ALLOWED_SPECIALIZED_SKILLS = List.of(
            "incident-data-diagnostics",
            "incident-code-grounding",
            "incident-operational-grounding",
            "none"
    );

    @Test
    void shouldCoverEveryOrchestratorClassificationWithOneFixture() throws Exception {
        var fixtures = fixtureFiles();

        assertEquals(EXPECTED_CLASSIFICATIONS.size(), fixtures.size());

        var actualClassifications = new LinkedHashSet<String>();
        for (var fixture : fixtures) {
            actualClassifications.add(frontmatter(fixture).get("expectedClassification"));
        }

        assertEquals(new LinkedHashSet<>(EXPECTED_CLASSIFICATIONS), actualClassifications);
    }

    @Test
    void shouldKeepAllFixturesAlignedWithDryRunContract() throws Exception {
        for (var fixture : fixtureFiles()) {
            var content = Files.readString(fixture);
            var metadata = frontmatter(fixture);
            var expectedClassification = metadata.get("expectedClassification");
            var specializedSkill = metadata.get("specializedSkill");

            assertFalse(metadata.getOrDefault("name", "").isBlank(), () -> "Missing fixture name in " + fixture);
            assertEquals(
                    CopilotIncidentRuntimeSkillNames.STARTER_SKILL_NAME,
                    metadata.get("starterSkill"),
                    () -> "Wrong starter skill in " + fixture
            );
            assertTrue(
                    ALLOWED_SPECIALIZED_SKILLS.contains(specializedSkill),
                    () -> "Unexpected specializedSkill in " + fixture + ": " + specializedSkill
            );

            assertContainsAll(content, fixture, List.of(
                    "# Fixture:",
                    "## Cel",
                    "## Minimalne Evidence",
                    "## Oczekiwany Dry Run Orkiestratora",
                    "## Oczekiwany Wklad Do Wyniku",
                    "## Antywzorce",
                    "Najpierw research flow przez `incident-analysis-orchestrator`.",
                    "Potem klasyfikacja jako `" + expectedClassification + "`.",
                    "`functionalAnalysis`",
                    "`technicalAnalysis`",
                    "`summary`, `recommendedAction`, `rationale`",
                    "`affectedFunction` ani `evidenceReferences`"
            ));

            if (!"none".equals(specializedSkill)) {
                assertTrue(
                        content.contains("Potem przejscie do `" + specializedSkill + "`."),
                        () -> "Fixture should route to specialized skill " + specializedSkill + ": " + fixture
                );
            }
        }
    }

    private static List<Path> fixtureFiles() throws IOException {
        try (var stream = Files.list(FIXTURE_ROOT)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
    }

    private static Map<String, String> frontmatter(Path fixture) throws IOException {
        var content = Files.readString(fixture);

        assertTrue(content.startsWith("---"), () -> "Missing frontmatter start in " + fixture);

        var endIndex = content.indexOf("\n---", 3);
        assertTrue(endIndex > 0, () -> "Missing frontmatter end in " + fixture);

        var metadata = new LinkedHashMap<String, String>();
        content.substring(3, endIndex)
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> {
                    var separatorIndex = line.indexOf(':');
                    assertTrue(separatorIndex > 0, () -> "Invalid frontmatter line in " + fixture + ": " + line);
                    metadata.put(
                            line.substring(0, separatorIndex).trim(),
                            line.substring(separatorIndex + 1).trim()
                    );
                });

        assertContainsKeys(metadata, fixture, List.of(
                "name",
                "expectedClassification",
                "starterSkill",
                "specializedSkill"
        ));
        return metadata;
    }

    private static void assertContainsKeys(Map<String, String> metadata, Path fixture, List<String> expectedKeys) {
        for (var expectedKey : expectedKeys) {
            assertTrue(metadata.containsKey(expectedKey), () -> "Missing frontmatter key in " + fixture + ": " + expectedKey);
        }
    }

    private static void assertContainsAll(String content, Path fixture, List<String> expectedFragments) {
        for (var expectedFragment : expectedFragments) {
            assertTrue(
                    content.contains(expectedFragment),
                    () -> "Missing expected fragment in " + fixture + ": " + expectedFragment
            );
        }
    }
}
