package pl.mkn.incidenttracker.integrations.gitlab;

import java.util.List;

public record GitLabRepositoryEndpointUseCaseInput(
        String projectName,
        String endpointId,
        List<String> httpMethods,
        String endpointPath,
        String controllerFilePath,
        int controllerLineStart,
        int controllerLineEnd
) {
    public GitLabRepositoryEndpointUseCaseInput {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
    }
}
