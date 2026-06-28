package pl.mkn.tdw.api.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaMethodUseCaseContextRequest;

import java.util.List;

public record GitLabJavaMethodUseCaseContextApiRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @Size(max = 700, message = "filePath must contain at most 700 characters")
        String filePath,
        @NotBlank(message = "className must not be blank")
        @Size(max = 300, message = "className must contain at most 300 characters")
        String className,
        @NotBlank(message = "methodName must not be blank")
        @Size(max = 120, message = "methodName must contain at most 120 characters")
        String methodName,
        @Min(value = 1, message = "lineNumber must be at least 1")
        Integer lineNumber,
        @Min(value = 0, message = "parameterCount must be at least 0")
        @Max(value = 50, message = "parameterCount must be at most 50")
        Integer parameterCount,
        @Size(max = 20, message = "parameterTypes must contain at most 20 entries")
        List<@Size(max = 300, message = "parameterTypes entries must contain at most 300 characters") String> parameterTypes,
        @Min(value = 1, message = "maxDepth must be at least 1")
        @Max(value = GitLabJavaMethodUseCaseContextRequest.MAX_MAX_DEPTH, message = "maxDepth must be at most 8")
        Integer maxDepth,
        @Min(value = 1, message = "maxResults must be at least 1")
        @Max(value = GitLabJavaMethodUseCaseContextRequest.MAX_MAX_RESULTS, message = "maxResults must be at most 100")
        Integer maxResults
) {

    GitLabJavaMethodUseCaseContextRequest toUseCaseRequest() {
        return new GitLabJavaMethodUseCaseContextRequest(
                projectName,
                filePath,
                className,
                methodName,
                lineNumber,
                parameterCount,
                parameterTypes,
                maxDepth,
                maxResults
        );
    }
}
