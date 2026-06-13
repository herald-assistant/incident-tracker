package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseEndpointIndex(
        List<GitLabEndpointUseCaseEndpointCandidate> endpoints,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseEndpointIndex {
        endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
