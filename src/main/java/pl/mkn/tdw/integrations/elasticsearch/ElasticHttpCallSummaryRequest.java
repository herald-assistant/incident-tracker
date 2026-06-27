package pl.mkn.tdw.integrations.elasticsearch;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ElasticHttpCallSummaryRequest(
        @NotBlank(message = "pathPattern must not be blank")
        @Size(max = 512, message = "pathPattern must be at most 512 characters")
        String pathPattern,
        @Size(max = 16, message = "method must be at most 16 characters") String method,
        @Size(max = 128, message = "serviceName must be at most 128 characters") String serviceName,
        @Min(value = 1, message = "timeWindowDays must be at least 1")
        @Max(value = 30, message = "timeWindowDays must be at most 30")
        Integer timeWindowDays,
        @Min(value = 1, message = "sampleSize must be at least 1")
        @Max(value = 1000, message = "sampleSize must be at most 1000")
        Integer sampleSize
) {
}
