package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryScopeScoringServiceTest {

    private final DeliveryScopeScoringService service = new DeliveryScopeScoringService();

    @Test
    void shouldReachTwoHundredOnlyWhenEveryDimensionHasMaximumIntensityAndScope() {
        var score = service.score(response(dimensions(dimension(100, 1.0))));

        assertThat(score.finalScore()).isEqualTo(200.0);
        assertThat(score.dimensions().structuralAndLogic()).satisfies(dimension -> {
            assertThat(dimension.scope()).isEqualTo(2.0);
            assertThat(dimension.scaledScore()).isEqualTo(200.0);
            assertThat(dimension.weight()).isEqualTo(0.25);
            assertThat(dimension.points()).isEqualTo(50.0);
        });
    }

    @Test
    void shouldCalculateFromTheSameRoundedScopeSignalThatIsExposedToTheUser() {
        var zero = dimension(0, 0.0);
        var score = service.score(response(new DeliveryScopeDimensions(
                zero,
                dimension(40, 0.7),
                zero,
                zero,
                zero,
                zero
        )));

        assertThat(score.dimensions().structuralAndLogic()).satisfies(dimension -> {
            assertThat(dimension.scopeSignal()).isEqualTo(0.7);
            assertThat(dimension.scope()).isEqualTo(1.6);
            assertThat(dimension.scaledScore()).isEqualTo(64.0);
            assertThat(dimension.points()).isEqualTo(16.0);
        });
        assertThat(score.finalScore()).isEqualTo(16.0);
    }

    @Test
    void shouldKeepZeroIntensityAtZeroRegardlessOfScope() {
        var score = service.score(response(dimensions(dimension(0, 1.0))));

        assertThat(score.finalScore()).isZero();
        assertThat(score.dimensions().distribution().scope()).isEqualTo(2.0);
    }

    @Test
    void shouldRejectInvalidDimensionAndMissingEvidence() {
        assertThatThrownBy(() -> new DeliveryScopeDimension(101, 0.5, List.of("evidence")))
                .hasMessage("dimension score must be between 0 and 100");
        assertThatThrownBy(() -> new DeliveryScopeDimension(20, 0.5, List.of()))
                .hasMessage("non-zero dimension score requires evidence");
    }

    private DeliveryScopeDimensions dimensions(DeliveryScopeDimension dimension) {
        return new DeliveryScopeDimensions(dimension, dimension, dimension, dimension, dimension, dimension);
    }

    private DeliveryScopeDimension dimension(int score, double scopeSignal) {
        return new DeliveryScopeDimension(
                score,
                scopeSignal,
                score > 0 ? List.of("artifact#section | observed fact") : List.of()
        );
    }

    private DeliveryAiResponse response(DeliveryScopeDimensions dimensions) {
        return new DeliveryAiResponse("DELIVERY", dimensions, 0.8, List.of(), List.of(), List.of());
    }
}
