package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabEndpointUseCaseEndpointResolutionStatus {
    RESOLVED,
    ENDPOINT_NOT_FOUND,
    AMBIGUOUS_ENDPOINT,
    INVALID_REQUEST
}
