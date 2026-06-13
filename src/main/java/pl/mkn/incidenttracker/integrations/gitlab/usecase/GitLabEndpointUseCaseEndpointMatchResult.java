package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseEndpointMatchResult(
        GitLabEndpointUseCaseEndpointCandidate endpoint,
        List<GitLabEndpointUseCaseEndpointCandidate> candidates,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseEndpointMatchResult {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    boolean matched() {
        return endpoint != null;
    }

    boolean ambiguous() {
        return endpoint == null && candidates.size() > 1;
    }
}
