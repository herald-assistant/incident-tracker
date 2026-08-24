package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.List;

public record DeliveryScopeDimension(
        int score,
        double scopeSignal,
        List<String> evidence
) {

    public DeliveryScopeDimension {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("dimension score must be between 0 and 100");
        }
        if (!Double.isFinite(scopeSignal) || scopeSignal < 0 || scopeSignal > 1) {
            throw new IllegalArgumentException("dimension scopeSignal must be between 0 and 1");
        }
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
        if (score > 0 && evidence.isEmpty()) {
            throw new IllegalArgumentException("non-zero dimension score requires evidence");
        }
    }
}
