package pl.mkn.incidenttracker.integrations.gitlab.usecase;

enum GitLabEndpointUseCaseCallResolutionStatus {
    RESOLVED,
    TERMINAL,
    UNRESOLVED,
    AMBIGUOUS
}
