package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public enum GitLabJavaMethodResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND,
    PARSE_FAILED,
    INVALID_REQUEST
}
