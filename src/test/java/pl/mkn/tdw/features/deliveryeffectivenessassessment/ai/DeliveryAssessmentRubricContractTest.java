package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryAssessmentRubricContractTest {

    private static final List<String> DIMENSIONS = List.of(
            "outcomeBreadth",
            "domainDecisionComplexity",
            "applicationFlowComplexity",
            "boundaryAndDataComplexity",
            "verificationStateSpace",
            "implementedCompatibilityScope"
    );

    @Test
    void shouldProvideBehavioralAnchorsForEveryDimension() throws IOException {
        var skill = new ClassPathResource(
                "copilot/skills/delivery-effectiveness-assessment-evaluator/SKILL.md"
        ).getContentAsString(StandardCharsets.UTF_8);

        DIMENSIONS.forEach(dimension -> assertThat(skill).contains("### `" + dimension + "`"));
        assertThat(StringUtils.countOccurrencesOf(skill, "- `0`:")).isGreaterThanOrEqualTo(6);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `2`:")).isGreaterThanOrEqualTo(6);
        assertThat(StringUtils.countOccurrencesOf(skill, "- `4`:")).isGreaterThanOrEqualTo(6);
        assertThat(skill)
                .contains("Brak danych")
                .contains("nie jest dowodem na `0`")
                .contains("## Przypadki kalibracyjne")
                .contains("nie szablony do dopasowania")
                .doesNotContain("historyczne Story Points");
    }

    @Test
    void shouldInlineEffectiveSkillAndArtifactEvidenceInOneShotPrompt() throws IOException {
        var packet = new DeliveryEvidencePacket(
                null,
                Map.of("delivery-effectiveness/issues.md", "# Jira delivery scope"),
                true,
                false,
                List.of()
        );
        var skill = new ClassPathResource(
                "copilot/skills/delivery-effectiveness-assessment-evaluator/SKILL.md"
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
                .contains("----- BEGIN EFFECTIVE SKILL: delivery-effectiveness-assessment-evaluator -----")
                .contains("### `outcomeBreadth`")
                .contains("Dla kazdego")
                .contains("niezerowego wymiaru")
                .contains("delivery-effectiveness/issues.md#ISSUE-KEY")
                .contains("INSUFFICIENT_EVIDENCE")
                .contains("\"dimensions\"")
                .contains("\"evidenceSummary\"")
                .contains("\"qualityFlags\"")
                .contains("\"visibilityLimits\"")
                .contains("----- BEGIN ARTIFACT: delivery-effectiveness/issues.md -----")
                .contains("# Jira delivery scope")
                .contains("----- END ARTIFACT: delivery-effectiveness/issues.md -----");
        assertThat(preparation.prompt()).doesNotContain("report_upsert_section");
        assertThat(preparation.artifacts()).containsKey("delivery-effectiveness/issues.md");
    }
}
