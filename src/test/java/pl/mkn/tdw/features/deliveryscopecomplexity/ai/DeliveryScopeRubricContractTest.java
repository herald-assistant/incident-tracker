package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeIssue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.material;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.mergeRequest;

class DeliveryScopeRubricContractTest {

    private static final List<String> DIMENSIONS = List.of(
            "novelty",
            "structuralAndLogic",
            "businessAndInvariants",
            "robustnessAndTests",
            "refactorAndArchitecture",
            "distribution"
    );

    @Test
    void shouldProvideBehavioralAnchorsForEveryDimension() throws IOException {
        var skill = new ClassPathResource(
                "copilot/skills/delivery-scope-complexity-evaluator/SKILL.md"
        ).getContentAsString(StandardCharsets.UTF_8);
        var normalizedSkill = skill.replaceAll("\\s+", " ");

        DIMENSIONS.forEach(dimension -> assertThat(skill).contains("### `" + dimension + "`"));
        assertThat(StringUtils.countOccurrencesOf(skill, "- `0-20`:")).isGreaterThanOrEqualTo(6);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `41-60`:")).isGreaterThanOrEqualTo(6);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `81-100`:")).isGreaterThanOrEqualTo(6);
        assertThat(normalizedSkill)
                .contains("niezaleznie od nazw typow Jira")
                .contains("KONTEKST POZA ZAKRESEM OCENY nie rozszerza dostawy")
                .doesNotContain("Klucz Jira typu Story, Bug albo Task")
                .contains("Brak danych")
                .contains("nie jest dowodem score `0`")
                .contains("Agregacja, wagi, zaokraglenia i wynik koncowy sa wyliczane deterministycznie poza modelem")
                .contains("Nie probuj przewidywac ani kalibrowac wyniku koncowego")
                .contains("scopeSignal")
                .contains("Nie sumuj score podzadan")
                .contains("Nie uzywaj jako bezposredniego sygnalu zlozonosci")
                .contains("przeniesienia istniejacych regul i inwariantow bez zmiany semantyki")
                .contains("Score mierzy odleglosc od juz istniejacego wzorca")
                .contains("kolejna prosta integracja pozostaje w `0-20`")
                .contains("Score `61+` wymaga nowego rodzaju zdolnosci")
                .contains("Punkty przyznawaj tylko za dodanie albo zmiane semantyki biznesowej")
                .contains("przeniesione bez zmiany znaczenia nie sa distinct")
                .doesNotContain(
                        "Story Points",
                        "## Kalibracja finalnego wyniku",
                        "scaledScore",
                        "finalScore",
                        "waga 15%",
                        "waga 25%",
                        "waga 20%"
                );
    }

    @Test
    void shouldInlineEffectiveSkillAndArtifactEvidenceInOneShotPrompt() throws IOException {
        var packet = new DeliveryEvidencePacket(
                null,
                Map.of("delivery-scope-complexity/issues.md", "# Jira delivery scope"),
                true,
                false,
                List.of()
        );
        var skill = new ClassPathResource(
                "copilot/skills/delivery-scope-complexity-evaluator/SKILL.md"
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
                .contains("----- BEGIN EFFECTIVE SKILL: delivery-scope-complexity-evaluator -----")
                .contains("### `novelty`")
                .contains("Dla kazdego")
                .contains("niezerowego score")
                .contains("delivery-scope-complexity/issues.md#ISSUE-KEY")
                .contains("INSUFFICIENT_EVIDENCE")
                .contains("\"dimensions\"")
                .contains("\"scopeSignal\"")
                .contains("\"distribution\"")
                .contains("\"evidenceSummary\"")
                .contains("\"qualityFlags\"")
                .contains("\"visibilityLimits\"")
                .contains("----- BEGIN ARTIFACT: delivery-scope-complexity/issues.md -----")
                .contains("# Jira delivery scope")
                .contains("----- END ARTIFACT: delivery-scope-complexity/issues.md -----");
        assertThat(preparation.prompt()).doesNotContain("report_upsert_section");
        assertThat(preparation.artifacts()).containsKey("delivery-scope-complexity/issues.md");
    }
    @Test
    void shouldCarrySemanticScopeWithPriorityOverConflictingCustomSkill() {
        var parent = material("CRM-100");
        var child = material(
                "CRM-124", "CRM Implementation", List.of(), parent, List.of());
        var issue = new DeliveryScopeIssue(
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
                .isLessThan(prompt.indexOf("----- BEGIN EFFECTIVE SKILL: delivery-scope-complexity-evaluator -----"));
    }
}
