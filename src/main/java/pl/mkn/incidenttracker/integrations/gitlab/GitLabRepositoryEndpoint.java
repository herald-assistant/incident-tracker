package pl.mkn.incidenttracker.integrations.gitlab;

import java.util.List;

public record GitLabRepositoryEndpoint(
        String endpointId,
        List<String> httpMethods,
        String path,
        String pathExpression,
        String controllerClass,
        String handlerMethod,
        String filePath,
        int lineStart,
        int lineEnd,
        List<String> requestTypes,
        List<String> responseTypes,
        List<String> annotations,
        String confidence,
        List<String> limitations,
        List<String> suggestedNextReads
) {
    public GitLabRepositoryEndpoint {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
        requestTypes = requestTypes != null ? List.copyOf(requestTypes) : List.of();
        responseTypes = responseTypes != null ? List.copyOf(responseTypes) : List.of();
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
    }
}
