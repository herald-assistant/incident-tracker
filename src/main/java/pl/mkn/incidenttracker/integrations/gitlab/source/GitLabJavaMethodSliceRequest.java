package pl.mkn.incidenttracker.integrations.gitlab.source;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GitLabJavaMethodSliceRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @NotBlank(message = "filePath must not be blank")
        @Size(max = 700, message = "filePath must contain at most 700 characters")
        String filePath,
        @Size(max = 300, message = "declaringTypeName must contain at most 300 characters")
        String declaringTypeName,
        @NotEmpty(message = "methodSelectors must contain at least one method selector")
        @Size(max = 20, message = "methodSelectors must contain at most 20 entries")
        List<@Valid GitLabJavaMethodSliceMethodSelector> methodSelectors,
        Boolean includeDirectPrivateHelpers,
        Boolean includeRelevantFields,
        Boolean includeRelevantImports,
        @Min(value = 1, message = "maxCharacters must be at least 1")
        @Max(value = GitLabJavaMethodSliceService.MAX_OUTPUT_CHARACTERS, message = "maxCharacters must be at most 40000")
        Integer maxCharacters
) {
    public GitLabJavaMethodSliceRequest {
        methodSelectors = methodSelectors != null ? List.copyOf(methodSelectors) : List.of();
    }
}
