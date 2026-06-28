package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record LocalAnalysisRunResultUpdateDecisionRequest(
        @NotNull(message = "aiResponse is required")
        JsonNode aiResponse
) {
}
