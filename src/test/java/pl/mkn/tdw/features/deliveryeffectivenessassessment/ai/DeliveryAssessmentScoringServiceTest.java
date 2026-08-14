package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssessmentScoringServiceTest {

    private final DeliveryAssessmentScoringService service = new DeliveryAssessmentScoringService();

    @Test
    void shouldApplyWeightedScoreAndTopBucketDeterministically() {
        var score = service.score(response(new DeliveryAssessmentDimensions(4, 4, 4, 4, 4, 4)));

        assertThat(score.score100()).isEqualTo(100);
        assertThat(score.deliveredStoryPoints()).isEqualTo(13);
    }

    @Test
    void shouldRespectBucketBoundaries() {
        assertThat(service.score(response(new DeliveryAssessmentDimensions(0, 0, 0, 0, 0, 0)))
                .deliveredStoryPoints()).isZero();
        assertThat(service.score(response(new DeliveryAssessmentDimensions(4, 0, 0, 0, 0, 0)))
                .deliveredStoryPoints()).isEqualTo(1);
        assertThat(service.score(response(new DeliveryAssessmentDimensions(0, 4, 0, 0, 0, 0)))
                .deliveredStoryPoints()).isEqualTo(2);
        assertThat(service.score(response(new DeliveryAssessmentDimensions(4, 4, 4, 0, 0, 0)))
                .deliveredStoryPoints()).isEqualTo(8);
    }

    private DeliveryAiResponse response(DeliveryAssessmentDimensions dimensions) {
        return new DeliveryAiResponse("DELIVERY", dimensions, 0.8, List.of("evidence"), List.of(), List.of());
    }
}
