package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.List;

public record DeliveryScopeDimensionScore(
        int score,
        double scopeSignal,
        double scope,
        double scaledScore,
        double weight,
        double points,
        List<String> evidence
) {

    public DeliveryScopeDimensionScore {
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }
}
