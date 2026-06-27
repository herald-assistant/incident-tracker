package pl.mkn.tdw.api.analysisruns;

import java.time.Instant;

public record LocalAnalysisRunListItemResponse(
        String analysisId,
        String feature,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
