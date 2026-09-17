package pl.mkn.tdw.agenttools.gitlab.mcp;

import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiOperationCandidate;

import java.util.List;
import java.util.Map;

public record GitLabOpenApiEndpointSliceToolResponse(
        String projectName,
        String branch,
        String commitId,
        String filePath,
        String status,
        String specType,
        String specVersion,
        String format,
        String httpMethod,
        String endpointPath,
        String matchedPath,
        String matchedBy,
        String operationId,
        String summary,
        String description,
        List<String> tags,
        String sourceRef,
        Map<String, Object> effectiveContext,
        Map<String, Object> operation,
        Map<String, Object> referencedComponents,
        List<GitLabOpenApiOperationCandidate> candidates,
        boolean truncated,
        List<String> omittedSections,
        List<String> unresolvedReferences,
        List<String> limitations
) {

    public static GitLabOpenApiEndpointSliceToolResponse from(
            GitLabOpenApiEndpointSliceResponse response,
            String branch,
            String commitId,
            String sourceRef
    ) {
        return new GitLabOpenApiEndpointSliceToolResponse(
                response.projectName(),
                branch != null ? branch : response.branch(),
                commitId,
                response.filePath(),
                response.status(),
                response.specType(),
                response.specVersion(),
                response.format(),
                response.httpMethod(),
                response.endpointPath(),
                response.matchedPath(),
                response.matchedBy(),
                response.operationId(),
                response.summary(),
                response.description(),
                response.tags(),
                sourceRef != null ? sourceRef : response.sourceRef(),
                response.effectiveContext(),
                response.operation(),
                response.referencedComponents(),
                response.candidates(),
                response.truncated(),
                response.omittedSections(),
                response.unresolvedReferences(),
                response.limitations()
        );
    }
}
