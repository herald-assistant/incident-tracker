package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import java.util.List;

public record DeliveryAssessmentScore(
        int deliveredStoryPoints,
        double score100,
        DeliveryAssessmentDimensions dimensions,
        double confidence,
        List<String> evidenceSummary,
        List<String> qualityFlags,
        List<String> visibilityLimits
) {

    public DeliveryAssessmentScore {
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        qualityFlags = qualityFlags != null ? List.copyOf(qualityFlags) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
