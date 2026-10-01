package pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentIssue;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentIssueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.source;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.material;

class DeliveryUnitBuilderTest {

    private final DeliveryUnitBuilder builder = new DeliveryUnitBuilder();

    @Test
    void shouldBuildConnectedComponentAndCountSharedMergeRequestOnce() {
        var shared = mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}");

        var units = builder.build(List.of(source("CRM-1", shared), source("CRM-2", shared)));

        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.unitId()).isEqualTo("DU-CRM-1-CRM-2");
            assertThat(unit.issues()).extracting(issue -> issue.issueKey())
                    .containsExactly("CRM-1", "CRM-2");
            assertThat(unit.mergeRequests()).containsExactly(shared);
        });
    }

    @Test
    void shouldKeepIssuesWithIndependentMergeRequestsInSeparateUnits() {
        var units = builder.build(List.of(
                source("CRM-1", mergeRequest(7, "src/A.java", "+A")),
                source("CRM-2", mergeRequest(8, "src/B.java", "+B"))
        ));

        assertThat(units).extracting(DeliveryUnit::unitId)
                .containsExactly("DU-CRM-1", "DU-CRM-2");
    }
    @Test
    void shouldKeepSharedParentAsContextWithoutMergingIndependentDeliveries() {
        var parent = material("CRM-100");
        var first = material("CRM-1", "CRM Implementation", List.of(), parent, List.of());
        var second = material("CRM-2", "CRM Implementation", List.of(), parent, List.of());
        var doneAt = Instant.parse("2026-07-10T10:00:00Z");

        var units = builder.build(List.of(
                new DeliveryAssessmentIssueSource(new DeliveryAssessmentIssue("CRM-1", doneAt, first, List.of()),
                        List.of(mergeRequest(7, "src/CustomerStatus.java", "+status")), List.of()),
                new DeliveryAssessmentIssueSource(new DeliveryAssessmentIssue("CRM-2", doneAt, second, List.of()),
                        List.of(mergeRequest(8, "src/CustomerContact.java", "+contact")), List.of())));

        assertThat(units).extracting(DeliveryUnit::unitId).containsExactly("DU-CRM-1", "DU-CRM-2");
        assertThat(units).allSatisfy(unit -> assertThat(unit.issues()).hasSize(1));
    }
}
