package pl.mkn.incidenttracker.integrations.elasticsearch;

import jakarta.validation.constraints.NotBlank;

public record ElasticLogSearchRequest(
        @NotBlank(message = "correlationId must not be blank") String correlationId
) {
}
