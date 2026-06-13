package pl.mkn.incidenttracker.integrations.gitlab.usecase;

enum GitLabEndpointUseCaseSpringBeanSourceKind {
    COMPONENT,
    CONFIGURATION_CLASS,
    BEAN_METHOD,
    MAPSTRUCT_MAPPER,
    FEIGN_CLIENT,
    SPRING_DATA_REPOSITORY
}
