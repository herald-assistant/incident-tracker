package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.DeliveryEvidencePacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                .contains("Brak danych")
                .contains("nie jest dowodem score `0`")
                .contains("Agregacja, wagi, zaokraglenia i wynik koncowy sa wyliczane deterministycznie poza modelem")
                .contains("Nie probuj przewidywac ani kalibrowac wyniku koncowego")
                .contains("scopeSignal")
                .contains("Nie sumuj score podzadan")
                .contains("Nie uzywaj jako bezposredniego sygnalu zlozonosci")
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
}
