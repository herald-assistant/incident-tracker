package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.Map;

public record DeliveryAssessmentAggregateResponse(
        int totalDeliveredStoryPoints,
        Map<Integer, Integer> distribution,
        int totalUnits,
        int assessedUnits,
        int excludedUnits,
        int notScorableUnits,
        int failedUnits,
        double coverage,
        String confidence,
        AnalysisAiUsage usage
) {

    public DeliveryAssessmentAggregateResponse {
        distribution = distribution != null ? Map.copyOf(distribution) : Map.of();
    }
}
