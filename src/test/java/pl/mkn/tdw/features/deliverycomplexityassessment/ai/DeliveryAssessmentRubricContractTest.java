package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentIssue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.material;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.mergeRequest;

class DeliveryAssessmentRubricContractTest {

    private static final List<String> DIMENSIONS = List.of(
            "outcomeBreadth",
            "domainDecisionComplexity",
            "applicationFlowComplexity",
            "boundaryAndDataComplexity",
            "verificationStateSpace",
            "implementedCompatibilityScope",
            "parameterizationComplexity"
    );

    @Test
    void shouldProvideBehavioralAnchorsForEveryDimension() throws IOException {
        var skill = new ClassPathResource(
                "copilot/skills/delivery-complexity-assessment-evaluator/SKILL.md"
        ).getContentAsString(StandardCharsets.UTF_8);
        var normalizedSkill = skill.replaceAll("\\s+", " ");

        DIMENSIONS.forEach(dimension -> assertThat(skill).contains("### `" + dimension + "`"));
        assertThat(StringUtils.countOccurrencesOf(skill, "- `0`:")).isGreaterThanOrEqualTo(7);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `2`:")).isGreaterThanOrEqualTo(7);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `4`:")).isGreaterThanOrEqualTo(7);
        assertThat(normalizedSkill)
                .contains("niezaleznie od nazw typow Jira")
                .contains("KONTEKST POZA ZAKRESEM OCENY nie rozszerza dostawy")
                .doesNotContain("Klucz Jira typu Story, Bug albo Task")
                .contains("Brak danych")
                .contains("nie jest dowodem na `0`")
                .contains("## Przypadki kalibracyjne")
                .contains("nie szablony do dopasowania")
                .contains("Parametryzacje oceniaj osobno w `parameterizationComplexity`")
                .contains("faktycznie dodana lub zmieniona mozliwosc sterowania zachowaniem")
                .contains("przeladowaniem w runtime")
                .contains("datami obowiazywania")
                .doesNotContain("historyczne Story Points");
    }

    @Test
    void shouldInlineEffectiveSkillAndArtifactEvidenceInOneShotPrompt() throws IOException {
        var packet = new DeliveryEvidencePacket(
                null,
                Map.of("delivery-complexity/issues.md", "# Jira delivery scope"),
                true,
                false,
                List.of()
        );
        var skill = new ClassPathResource(
                "copilot/skills/delivery-complexity-assessment-evaluator/SKILL.md"
        ).getContentAsString(StandardCharsets.UTF_8);
        var skillRuntimeLoader = mock(CopilotSkillRuntimeLoader.class);
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(new CopilotRuntimeSkill(
                DeliveryPromptPreparationService.SKILL_NAME,
                "Assessment",
                Math.toIntExact(skill.lines().count()),
                skill,
                skill,
                CopilotRuntimeSkillState.DEFAULT,
                true
        )));

        var preparation = new DeliveryPromptPreparationService(skillRuntimeLoader).prepare(packet);

        assertThat(preparation.prompt())
                .contains("## Zakres oceny i hierarchia zadan")
                .contains("Ten kontrakt zakresu oceny ma pierwszenstwo")
                .contains("To jest jednokrokowy request")
                .contains("Nie wywoluj toola `skill`")
                .contains("----- BEGIN EFFECTIVE SKILL: delivery-complexity-assessment-evaluator -----")
                .contains("### `outcomeBreadth`")
                .contains("Dla kazdego")
                .contains("niezerowego wymiaru")
                .contains("delivery-complexity/issues.md#ISSUE-KEY")
                .contains("INSUFFICIENT_EVIDENCE")
                .contains("\"dimensions\"")
                .contains("\"parameterizationComplexity\"")
                .contains("\"evidenceSummary\"")
                .contains("\"qualityFlags\"")
                .contains("\"visibilityLimits\"")
                .contains("----- BEGIN ARTIFACT: delivery-complexity/issues.md -----")
                .contains("# Jira delivery scope")
                .contains("----- END ARTIFACT: delivery-complexity/issues.md -----");
        assertThat(preparation.prompt()).doesNotContain("report_upsert_section");
        assertThat(preparation.artifacts()).containsKey("delivery-complexity/issues.md");
    }
    @Test
    void shouldCarrySemanticScopeWithPriorityOverConflictingCustomSkill() {
        var parent = material("CRM-100");
        var child = material(
                "CRM-124", "CRM Implementation", List.of(), parent, List.of());
        var issue = new DeliveryAssessmentIssue(
                child.issueKey(), Instant.parse("2026-07-10T10:00:00Z"), child, List.of());
        var unit = new DeliveryUnit("DU-CRM-124", List.of(issue),
                List.of(mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}")), List.of());
        var packet = new DeliveryEvidencePacketBuilder().build(unit);
        var customSkill = "Klucz Jira typu Story, Bug albo Task jest jedna jednostka oceny.";
        var loader = mock(CopilotSkillRuntimeLoader.class);
        when(loader.availableSkills()).thenReturn(List.of(new CopilotRuntimeSkill(
                DeliveryPromptPreparationService.SKILL_NAME, "Assessment", 1,
                customSkill, customSkill, CopilotRuntimeSkillState.CUSTOM, true)));

        var prompt = new DeliveryPromptPreparationService(loader).prepare(packet).prompt();

        assertThat(prompt).contains(
                "Oceniane zadania: CRM-124",
                "Zadanie podrzedne wobec: CRM-100",
                "## CRM-100\n\n- Zakres oceny: KONTEKST POZA ZAKRESEM OCENY",
                "Ten kontrakt zakresu oceny ma pierwszenstwo", customSkill);
        assertThat(prompt).doesNotContain("Type:", "CRM Implementation");
        assertThat(prompt.indexOf("## Zakres oceny i hierarchia zadan"))
                .isLessThan(prompt.indexOf("----- BEGIN EFFECTIVE SKILL: delivery-complexity-assessment-evaluator -----"));
    }

    @Test
    void shouldSeparateStagesAndUseOnlyCurrentScopeWithUnchangedEffectiveRubric() {
        var packet = new DeliveryEvidencePacket(null, Map.of(
                "delivery-complexity/issues.md", "Full CRM Jira and Confluence material",
                "delivery-complexity/diffs.md", "Complete CRM Customer diff",
                "delivery-complexity/visibility.md", "CRM runtime unavailable"), true, false, List.of());
        var prompts = new DeliveryPromptPreparationService(mock(CopilotSkillRuntimeLoader.class));
        var custom = "CUSTOM rubric: kotwice bez zmian; zwroc coverage calej dostawy.";
        var scope = List.of("CRM/customer-api!1#file:0:src/Customer.java");
        var manifest = "Whole CRM delivery: Customer.java and Notification.java";
        var part = prompts.prepareFindings(packet, manifest, scope, custom);
        var reduction = prompts.prepareReduction(packet, manifest, "[]", scope, custom);

        assertThat(part.prompt()).startsWith("ETAP: EVIDENCE_PART").contains("Nie zwracaj coverage", "part-scope.json", "CUSTOM");
        assertThat(reduction.prompt()).startsWith("ETAP: REDUCTION")
                .contains("Zachowaj każdy behaviorId i jego wymiary", "dokładne wpisy dependencies");
        for (var prepared : List.of(part, reduction)) {
            assertThat(prepared.effectiveSkill()).isEqualTo(custom);
            assertThat(prepared.artifacts().get("delivery-complexity/issues.md")).isEqualTo(packet.artifacts().get("delivery-complexity/issues.md"));
            assertThat(prepared.artifacts().get("delivery-complexity/part-scope.json")).isEqualTo(scopeJson(scope));
            assertThat(prepared.artifacts().get("delivery-complexity/manifest.md")).isEqualTo(manifest);
            assertThat(prepared.prompt().substring(prepared.prompt().indexOf("## Jedyna odpowiedź"))).doesNotContain("\"coverage\"", "Notification.java");
        }
        assertThat(part.artifacts().get("delivery-complexity/diffs.md")).isEqualTo(packet.artifacts().get("delivery-complexity/diffs.md"));
        assertThat(reduction.artifacts()).doesNotContainKey("delivery-complexity/diffs.md");
    }

    private String scopeJson(List<String> scope) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scope); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new AssertionError(failure); }
    }
}
