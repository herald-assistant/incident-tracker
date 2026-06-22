package pl.mkn.incidenttracker.integrations.gitlab.openapi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitLabOpenApiEndpointSliceRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @NotBlank(message = "filePath must not be blank")
        @Size(max = 700, message = "filePath must contain at most 700 characters")
        String filePath,
        @NotBlank(message = "httpMethod must not be blank")
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        @NotBlank(message = "endpointPath must not be blank")
        @Size(max = 300, message = "endpointPath must contain at most 300 characters")
        String endpointPath,
        Boolean includeReferencedSchemas,
        @Min(value = 0, message = "schemaDepth must be at least 0")
        @Max(value = GitLabOpenApiEndpointSliceService.MAX_SCHEMA_DEPTH, message = "schemaDepth must be at most 4")
        Integer schemaDepth,
        @Min(value = 1, message = "maxCharacters must be at least 1")
        @Max(value = GitLabOpenApiEndpointSliceService.MAX_OUTPUT_CHARACTERS, message = "maxCharacters must be at most 50000")
        Integer maxCharacters
) {
}
