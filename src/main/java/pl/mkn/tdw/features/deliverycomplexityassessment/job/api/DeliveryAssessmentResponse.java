package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAssessmentDimensions;

import java.util.List;

public record DeliveryAssessmentResponse(
        int deliveredStoryPoints,
        double score100,
        DeliveryAssessmentDimensions dimensions,
        double confidence,
        List<String> evidenceSummary,
        List<String> qualityFlags
) {

    public DeliveryAssessmentResponse {
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        qualityFlags = qualityFlags != null ? List.copyOf(qualityFlags) : List.of();
    }
}
