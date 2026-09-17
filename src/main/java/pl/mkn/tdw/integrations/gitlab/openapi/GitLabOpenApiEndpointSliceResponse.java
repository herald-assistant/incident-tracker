package pl.mkn.tdw.integrations.gitlab.openapi;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

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
        List<String> limitations,
        String format,
        String matchedBy,
        Map<String, Object> effectiveContext,
        Map<String, Object> operation,
        Map<String, Object> referencedComponents,
        List<GitLabOpenApiOperationCandidate> candidates,
        List<String> unresolvedReferences,
        List<String> omittedSections
) {

    public GitLabOpenApiEndpointSliceResponse {
        tags = tags != null ? List.copyOf(tags) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        effectiveContext = immutableMap(effectiveContext);
        operation = immutableMap(operation);
        referencedComponents = immutableMap(referencedComponents);
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        unresolvedReferences = unresolvedReferences != null ? List.copyOf(unresolvedReferences) : List.of();
        omittedSections = omittedSections != null ? List.copyOf(omittedSections) : List.of();
    }

    public GitLabOpenApiEndpointSliceResponse(
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
        this(group, projectName, branch, filePath, status, specType, specVersion, httpMethod,
                endpointPath, matchedPath, operationId, summary, description, tags, sourceRef,
                content, returnedCharacters, truncated, limitations, null, null, Map.of(),
                Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(value))
                : Map.of();
    }
}
