package pl.mkn.incidenttracker.integrations.gitlab.source;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitLabJavaMethodSliceMethodSelector(
        @NotBlank(message = "methodName must not be blank")
        @Size(max = 120, message = "methodName must contain at most 120 characters")
        String methodName,
        @Min(value = 1, message = "lineStart must be at least 1")
        Integer lineStart
) {
}
