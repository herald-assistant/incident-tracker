package pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.source;

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
}
