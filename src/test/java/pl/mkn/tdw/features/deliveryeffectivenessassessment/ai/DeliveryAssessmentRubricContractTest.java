package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldRequireArtifactEvidenceWithoutChangingResultFields() {
        var packet = new DeliveryEvidencePacket(
                null,
                Map.of("delivery-effectiveness/issues.md", "# Jira delivery scope"),
                true,
                false,
                List.of()
        );

        var preparation = new DeliveryPromptPreparationService().prepare(packet);

        assertThat(preparation.prompt())
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
        assertThat(preparation.artifacts()).containsKey("delivery-effectiveness/issues.md");
    }
}
