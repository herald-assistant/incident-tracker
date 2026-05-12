package pl.mkn.incidenttracker.integrations.elasticsearch;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public record ElasticHttpCallLogsRequest(
        @Size(max = 128, message = "correlationId must be at most 128 characters") String correlationId,
        @Size(max = 512, message = "path must be at most 512 characters") String path,
        @Min(value = 100, message = "status must be a valid HTTP status code")
        @Max(value = 599, message = "status must be a valid HTTP status code")
        Integer status,
        @Size(max = 16, message = "method must be at most 16 characters") String method,
        @Min(value = 1, message = "timeWindowDays must be at least 1")
        @Max(value = 30, message = "timeWindowDays must be at most 30")
        Integer timeWindowDays,
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 200, message = "size must be at most 200")
        Integer size,
        ElasticLogDetailLevel detailLevel
) {
    @AssertTrue(message = "correlationId or path must be provided")
    public boolean hasCorrelationIdOrPath() {
        return StringUtils.hasText(correlationId) || StringUtils.hasText(path);
    }
}
