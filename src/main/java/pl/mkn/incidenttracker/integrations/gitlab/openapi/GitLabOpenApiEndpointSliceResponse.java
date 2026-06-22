package pl.mkn.incidenttracker.integrations.gitlab.openapi;

import java.util.List;

public record GitLabOpenApiEndpointSliceResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        String status,
        String specType,
        String specVersion,
        String httpMethod,
        String endpointPath,
        String matchedPath,
        String operationId,
        String summary,
        String description,
        List<String> tags,
        String sourceRef,
        String content,
        int returnedCharacters,
        boolean truncated,
        List<String> limitations
) {

    public GitLabOpenApiEndpointSliceResponse {
        tags = tags != null ? List.copyOf(tags) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
