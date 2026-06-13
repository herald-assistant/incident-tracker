package pl.mkn.incidenttracker.integrations.gitlab.usecase;

enum GitLabEndpointUseCaseInjectionSourceKind {
    CONSTRUCTOR,
    LOMBOK_REQUIRED_ARGS_CONSTRUCTOR,
    FIELD,
    METHOD,
    RECORD_COMPONENT
}
