package pl.mkn.incidenttracker.integrations.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitLabRepositoryEndpointListRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @Size(max = 200, message = "endpointPathPrefix must contain at most 200 characters")
        String endpointPathPrefix,
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        @Min(value = 1, message = "maxScannedFiles must be at least 1")
        @Max(value = 250, message = "maxScannedFiles must be at most 250")
        Integer maxScannedFiles
) {
}
