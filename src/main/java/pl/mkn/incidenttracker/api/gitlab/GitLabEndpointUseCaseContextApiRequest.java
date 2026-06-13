package pl.mkn.incidenttracker.api.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseOutputMode;

public record GitLabEndpointUseCaseContextApiRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @Size(max = 500, message = "endpointId must contain at most 500 characters")
        String endpointId,
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        @Size(max = 300, message = "endpointPath must contain at most 300 characters")
        String endpointPath,
        @Size(max = 300, message = "sourcePathPrefix must contain at most 300 characters")
        String sourcePathPrefix,
        GitLabEndpointUseCaseOutputMode outputMode,
        @Min(value = 1, message = "maxDepth must be at least 1")
        @Max(value = 20, message = "maxDepth must be at most 20")
        Integer maxDepth,
        @Min(value = 1, message = "maxNodes must be at least 1")
        @Max(value = 200, message = "maxNodes must be at most 200")
        Integer maxNodes,
        Boolean includeAsyncConsumers,
        @Size(max = 500, message = "reason must contain at most 500 characters")
        String reason
) {
    GitLabEndpointUseCaseContextRequest toIntegrationRequest() {
        return new GitLabEndpointUseCaseContextRequest(
                projectName,
                endpointId,
                httpMethod,
                endpointPath,
                sourcePathPrefix,
                outputMode,
                maxDepth,
                maxNodes,
                includeAsyncConsumers,
                reason
        );
    }
}
