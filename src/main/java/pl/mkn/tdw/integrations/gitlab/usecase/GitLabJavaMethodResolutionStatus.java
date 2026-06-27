package pl.mkn.tdw.integrations.gitlab.usecase;

public enum GitLabJavaMethodResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND,
    PARSE_FAILED,
    INVALID_REQUEST
}
