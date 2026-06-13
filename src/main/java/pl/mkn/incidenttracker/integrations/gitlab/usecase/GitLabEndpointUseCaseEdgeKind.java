package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabEndpointUseCaseEdgeKind {
    SYNC_CALL,
    VALIDATION,
    MAPPING,
    REPOSITORY_READ,
    REPOSITORY_WRITE,
    EXTERNAL_CALL,
    EVENT_PUBLISH,
    ASYNC_BOUNDARY,
    CONFIGURES_BEAN,
    INHERITANCE_CALL,
    UNRESOLVED_CALL
}
