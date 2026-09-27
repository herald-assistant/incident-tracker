package pl.mkn.tdw.integrations.gitlab.contract.usecase;

public enum GitLabJavaMethodResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND,
    PARSE_FAILED,
    INVALID_REQUEST
}
