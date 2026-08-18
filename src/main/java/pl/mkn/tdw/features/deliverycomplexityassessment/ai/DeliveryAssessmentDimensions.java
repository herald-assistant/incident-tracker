package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

public record DeliveryAssessmentDimensions(
        int outcomeBreadth,
        int domainDecisionComplexity,
        int applicationFlowComplexity,
        int boundaryAndDataComplexity,
        int verificationStateSpace,
        int implementedCompatibilityScope
) {

    public DeliveryAssessmentDimensions {
        validate("outcomeBreadth", outcomeBreadth);
        validate("domainDecisionComplexity", domainDecisionComplexity);
        validate("applicationFlowComplexity", applicationFlowComplexity);
        validate("boundaryAndDataComplexity", boundaryAndDataComplexity);
        validate("verificationStateSpace", verificationStateSpace);
        validate("implementedCompatibilityScope", implementedCompatibilityScope);
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 4) {
            throw new IllegalArgumentException(name + " must be between 0 and 4");
        }
    }
}
