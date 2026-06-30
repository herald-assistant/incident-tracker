package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record LocalAnalysisRunDetailResponse(
        String analysisId,
        String feature,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        JsonNode exportEnvelope,
        boolean continuationEnabled
) {
}
