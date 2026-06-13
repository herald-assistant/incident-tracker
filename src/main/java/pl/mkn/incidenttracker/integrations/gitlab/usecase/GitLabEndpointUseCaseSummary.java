package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseSummary(
        String mainResponsibility,
        List<String> businessObjects,
        List<String> sideEffects,
        List<String> externalSystems,
        List<String> asyncBoundaries
) {
    public GitLabEndpointUseCaseSummary {
        businessObjects = businessObjects != null ? List.copyOf(businessObjects) : List.of();
        sideEffects = sideEffects != null ? List.copyOf(sideEffects) : List.of();
        externalSystems = externalSystems != null ? List.copyOf(externalSystems) : List.of();
        asyncBoundaries = asyncBoundaries != null ? List.copyOf(asyncBoundaries) : List.of();
    }

    public static GitLabEndpointUseCaseSummary empty() {
        return new GitLabEndpointUseCaseSummary(null, List.of(), List.of(), List.of(), List.of());
    }
}
