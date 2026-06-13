package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseResolvedDependency(
        GitLabEndpointUseCaseInjectionPoint injectionPoint,
        GitLabEndpointUseCaseDependencyResolutionStatus status,
        GitLabEndpointUseCaseSpringBean resolvedBean,
        List<GitLabEndpointUseCaseSpringBean> candidates,
        GitLabEndpointUseCaseResolutionKind resolutionKind
) {
    GitLabEndpointUseCaseResolvedDependency {
        status = status != null ? status : GitLabEndpointUseCaseDependencyResolutionStatus.UNRESOLVED;
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        resolutionKind = resolutionKind != null ? resolutionKind : GitLabEndpointUseCaseResolutionKind.UNRESOLVED;
    }

    boolean resolved() {
        return status == GitLabEndpointUseCaseDependencyResolutionStatus.RESOLVED && resolvedBean != null;
    }
}
