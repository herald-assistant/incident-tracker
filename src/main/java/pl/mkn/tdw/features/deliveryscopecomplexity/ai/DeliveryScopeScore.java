package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.List;

public record DeliveryScopeScore(
        double finalScore,
        DeliveryScopeScoreBreakdown dimensions,
        double confidence,
        List<String> evidenceSummary,
        List<String> qualityFlags,
        List<String> visibilityLimits
) {

    public DeliveryScopeScore {
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        qualityFlags = qualityFlags != null ? List.copyOf(qualityFlags) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
