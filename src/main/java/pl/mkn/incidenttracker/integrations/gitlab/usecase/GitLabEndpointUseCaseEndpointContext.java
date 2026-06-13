package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseEndpointContext(
        String endpointId,
        List<String> httpMethods,
        String inputPath,
        String matchedPathPattern,
        String controllerClass,
        String controllerMethod,
        String sourcePath,
        int lineStart,
        int lineEnd
) {
    public GitLabEndpointUseCaseEndpointContext {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
    }
}
