package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

public record DeliveryScopeDimensions(
        DeliveryScopeDimension novelty,
        DeliveryScopeDimension structuralAndLogic,
        DeliveryScopeDimension businessAndInvariants,
        DeliveryScopeDimension robustnessAndTests,
        DeliveryScopeDimension refactorAndArchitecture,
        DeliveryScopeDimension distribution
) {

    public DeliveryScopeDimensions {
        if (novelty == null
                || structuralAndLogic == null
                || businessAndInvariants == null
                || robustnessAndTests == null
                || refactorAndArchitecture == null
                || distribution == null) {
            throw new IllegalArgumentException("all Delivery Scope Complexity dimensions are required");
        }
    }
}
