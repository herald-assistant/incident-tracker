package pl.mkn.tdw.api.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;

public record GitLabEndpointUseCaseContextApiRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @Size(max = 500, message = "endpointId must contain at most 500 characters")
        String endpointId,
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        @Size(max = 200, message = "endpointPath must contain at most 200 characters")
        String endpointPath,
        @Min(value = 1, message = "maxDepth must be at least 1")
        @Max(value = GitLabEndpointUseCaseContextRequest.MAX_MAX_DEPTH, message = "maxDepth must be at most 8")
        Integer maxDepth,
        @Min(value = 1, message = "maxFiles must be at least 1")
        @Max(value = GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES, message = "maxFiles must be at most 100")
        Integer maxFiles
) {

    GitLabEndpointUseCaseContextRequest toUseCaseRequest() {
        return new GitLabEndpointUseCaseContextRequest(
                projectName,
                endpointId,
                httpMethod,
                endpointPath,
                maxDepth,
                maxFiles
        );
    }
}
