package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabEndpointUseCaseResolutionKind {
    DIRECT_METHOD,
    THIS_METHOD,
    SUPER_METHOD,
    INHERITED_METHOD,
    SPRING_BEAN,
    SPRING_BEAN_POLYMORPHIC,
    STATIC_METHOD,
    NEW_INSTANCE,
    EXTERNAL_LIBRARY,
    UNRESOLVED
}
