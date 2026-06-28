package pl.mkn.tdw.integrations.gitlab.usecase;

public enum GitLabJavaMethodUseCaseEntryStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND,
    PARSE_FAILED,
    READ_FAILED,
    INVALID_REQUEST
}
