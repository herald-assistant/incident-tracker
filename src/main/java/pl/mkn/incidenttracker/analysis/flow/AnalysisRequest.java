package pl.mkn.incidenttracker.analysis.flow;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
        @NotBlank(message = "correlationId must not be blank") String correlationId
) {
}
