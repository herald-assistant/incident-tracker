package pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.unit;

class DeliveryEvidencePacketBuilderTest {

    @Test
    void shouldBuildScorablePacketWithoutPersonalDataOrStoryPoints() {
        var builder = new DeliveryEvidencePacketBuilder(new DeliveryEffectivenessAssessmentProperties());

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}")
        ));
        var allArtifacts = String.join("\n", packet.artifacts().values());

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isFalse();
        assertThat(allArtifacts)
                .contains("CRM-123", "CustomerStatus")
                .doesNotContain("Sensitive Person", "Sensitive Author", "private comment", "Story Points");
    }

    @Test
    void shouldExcludeMechanicalOnlyChangesBeforeAi() {
        var builder = new DeliveryEvidencePacketBuilder(new DeliveryEffectivenessAssessmentProperties());

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "frontend/dist/main.min.js", "+minified")
        ));

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isTrue();
    }

    @Test
    void shouldExposeDiffTruncationAsVisibilityLimit() {
        var properties = new DeliveryEffectivenessAssessmentProperties();
        properties.setMaxDiffCharactersPerUnit(48);
        var builder = new DeliveryEvidencePacketBuilder(properties);

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", "x".repeat(200))
        ));

        assertThat(packet.visibilityLimits())
                .anyMatch(limit -> limit.contains("Diff content was truncated"));
    }
}
