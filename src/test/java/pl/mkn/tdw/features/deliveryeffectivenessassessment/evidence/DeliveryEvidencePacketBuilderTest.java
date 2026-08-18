package pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.unit;

class DeliveryEvidencePacketBuilderTest {

    @Test
    void shouldBuildScorablePacketWithoutPersonalDataOrStoryPoints() {
        var builder = new DeliveryEvidencePacketBuilder();

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}")
        ));
        var allArtifacts = String.join("\n", packet.artifacts().values());

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isFalse();
        assertThat(allArtifacts)
                .contains("CRM-123", "CustomerStatus")
                .doesNotContain("jira-comment-author-1", "mr-author-101", "internal comment payload", "Story Points");
    }

    @Test
    void shouldExcludeMechanicalOnlyChangesBeforeAi() {
        var builder = new DeliveryEvidencePacketBuilder();

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "frontend/dist/main.min.js", "+minified")
        ));

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isTrue();
    }

    @Test
    void shouldPreserveCompleteDiffWithoutLocalCharacterBudget() {
        var builder = new DeliveryEvidencePacketBuilder();
        var completeDiff = "x".repeat(75_000);

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", completeDiff)
        ));

        assertThat(packet.artifacts().get("delivery-effectiveness/diffs.md"))
                .contains(completeDiff);
        assertThat(packet.visibilityLimits())
                .noneMatch(limit -> limit.contains("truncated"));
    }
}
