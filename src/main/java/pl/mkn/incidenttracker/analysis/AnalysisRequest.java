package pl.mkn.incidenttracker.analysis;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
        @NotBlank(message = "correlationId must not be blank") String correlationId
) {
}
