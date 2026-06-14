package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseEndpointResolution(
        GitLabEndpointUseCaseEndpointResolutionStatus status,
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabEndpointUseCaseEndpointContext endpoint,
        List<GitLabEndpointUseCaseEndpointContext> candidates,
        List<String> limitations
) {
    public GitLabEndpointUseCaseEndpointResolution {
        status = status != null ? status : GitLabEndpointUseCaseEndpointResolutionStatus.INVALID_REQUEST;
        candidates = GitLabEndpointUseCaseModelSupport.copy(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }
}
