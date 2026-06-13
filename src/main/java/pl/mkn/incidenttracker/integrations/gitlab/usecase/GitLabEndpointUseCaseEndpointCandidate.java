package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseEndpointCandidate(
        String endpointId,
        List<String> httpMethods,
        String pathPattern,
        String pathExpression,
        String controllerClass,
        String controllerMethod,
        String controllerMethodId,
        String sourcePath,
        Integer lineStart,
        Integer lineEnd,
        List<String> requestTypes,
        List<String> responseTypes,
        List<String> annotations,
        GitLabEndpointUseCaseConfidence confidence,
        List<String> limitations
) {
    GitLabEndpointUseCaseEndpointCandidate {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
        requestTypes = requestTypes != null ? List.copyOf(requestTypes) : List.of();
        responseTypes = responseTypes != null ? List.copyOf(responseTypes) : List.of();
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.MEDIUM;
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    GitLabEndpointUseCaseEndpointContext toEndpointContext() {
        return new GitLabEndpointUseCaseEndpointContext(
                endpointId,
                httpMethods,
                pathPattern,
                pathPattern,
                controllerClass,
                controllerMethod,
                sourcePath,
                lineStart,
                lineEnd
        );
    }
}
