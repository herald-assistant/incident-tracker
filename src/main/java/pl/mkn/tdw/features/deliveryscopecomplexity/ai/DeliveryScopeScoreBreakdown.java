package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

public record DeliveryScopeScoreBreakdown(
        DeliveryScopeDimensionScore novelty,
        DeliveryScopeDimensionScore structuralAndLogic,
        DeliveryScopeDimensionScore businessAndInvariants,
        DeliveryScopeDimensionScore robustnessAndTests,
        DeliveryScopeDimensionScore refactorAndArchitecture,
        DeliveryScopeDimensionScore distribution
) {
}
