package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseGraphBuildResult(
        GitLabEndpointUseCaseGraph graph,
        GitLabEndpointUseCaseLimits limits,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseGraphBuildResult {
        graph = graph != null ? graph : GitLabEndpointUseCaseGraph.empty();
        limits = limits != null ? limits : GitLabEndpointUseCaseLimits.defaults();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
