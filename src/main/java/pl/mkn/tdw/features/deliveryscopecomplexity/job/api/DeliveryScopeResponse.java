package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryScopeScoreBreakdown;

import java.util.List;

public record DeliveryScopeResponse(
        double finalScore,
        DeliveryScopeScoreBreakdown dimensions,
        double confidence,
        List<String> evidenceSummary,
        List<String> qualityFlags
) {

    public DeliveryScopeResponse {
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        qualityFlags = qualityFlags != null ? List.copyOf(qualityFlags) : List.of();
    }
}
