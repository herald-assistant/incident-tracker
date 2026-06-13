package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabEndpointUseCaseIndexStatus {
    BUILT_DURING_CALL,
    USED_REQUEST_CACHE,
    PARTIAL,
    NOT_BUILT
}
