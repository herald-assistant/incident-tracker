package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record DeliveryScopeAggregateResponse(
        double totalComplexityPoints,
        double averageComplexityScore,
        int totalUnits,
        int assessedUnits,
        int excludedUnits,
        int notScorableUnits,
        int failedUnits,
        double coverage,
        String confidence,
        AnalysisAiUsage usage
) {
}
