package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DeliveryScopeScoringService {

    public DeliveryScopeScore score(DeliveryAiResponse response) {
        if (response == null || response.dimensions() == null) {
            throw new IllegalArgumentException("Delivery AI response does not contain dimensions.");
        }
        var dimensions = response.dimensions();
        var breakdown = new DeliveryScopeScoreBreakdown(
                dimension(dimensions.novelty(), 0.15),
                dimension(dimensions.structuralAndLogic(), 0.25),
                dimension(dimensions.businessAndInvariants(), 0.15),
                dimension(dimensions.robustnessAndTests(), 0.10),
                dimension(dimensions.refactorAndArchitecture(), 0.15),
                dimension(dimensions.distribution(), 0.20)
        );
        var finalScore = round1(Math.min(200.0,
                breakdown.novelty().points()
                        + breakdown.structuralAndLogic().points()
                        + breakdown.businessAndInvariants().points()
                        + breakdown.robustnessAndTests().points()
                        + breakdown.refactorAndArchitecture().points()
                        + breakdown.distribution().points()));
        return new DeliveryScopeScore(
                finalScore,
                breakdown,
                response.confidence(),
                response.evidenceSummary(),
                response.qualityFlags(),
                response.visibilityLimits()
        );
    }

    private DeliveryScopeDimensionScore dimension(DeliveryScopeDimension dimension, double weight) {
        var scopeSignal = round1(dimension.scopeSignal());
        var scope = BigDecimal.valueOf(scopeSignal)
                .multiply(BigDecimal.valueOf(1.5))
                .add(BigDecimal.valueOf(0.5))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
        var scaledScore = multiplyAndRound1(dimension.score(), scope);
        return new DeliveryScopeDimensionScore(
                dimension.score(),
                scopeSignal,
                scope,
                scaledScore,
                weight,
                multiplyAndRound1(scaledScore, weight),
                dimension.evidence()
        );
    }

    private double multiplyAndRound1(double left, double right) {
        return BigDecimal.valueOf(left)
                .multiply(BigDecimal.valueOf(right))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
