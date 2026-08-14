package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import org.springframework.stereotype.Component;

@Component
public class DeliveryAssessmentScoringService {

    public DeliveryAssessmentScore score(DeliveryAiResponse response) {
        if (response == null || response.dimensions() == null) {
            throw new IllegalArgumentException("Delivery AI response does not contain dimensions.");
        }
        var dimensions = response.dimensions();
        var score100 = 10.0 * dimensions.outcomeBreadth() / 4.0
                + 25.0 * dimensions.domainDecisionComplexity() / 4.0
                + 25.0 * dimensions.applicationFlowComplexity() / 4.0
                + 15.0 * dimensions.boundaryAndDataComplexity() / 4.0
                + 15.0 * dimensions.verificationStateSpace() / 4.0
                + 10.0 * dimensions.implementedCompatibilityScope() / 4.0;
        return new DeliveryAssessmentScore(
                bucket(score100),
                Math.round(score100 * 100.0) / 100.0,
                dimensions,
                response.confidence(),
                response.evidenceSummary(),
                response.qualityFlags(),
                response.visibilityLimits()
        );
    }

    private int bucket(double score100) {
        if (score100 <= 0) {
            return 0;
        }
        if (score100 <= 14) {
            return 1;
        }
        if (score100 <= 27) {
            return 2;
        }
        if (score100 <= 42) {
            return 3;
        }
        if (score100 <= 58) {
            return 5;
        }
        if (score100 <= 75) {
            return 8;
        }
        return 13;
    }
}
