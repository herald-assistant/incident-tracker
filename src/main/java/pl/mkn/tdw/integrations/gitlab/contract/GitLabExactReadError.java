package pl.mkn.tdw.integrations.gitlab.contract;

public enum GitLabExactReadError {
    INVALID_TARGET,
    CONNECTION_NOT_FOUND,
    CONNECTION_INVALID,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    UPSTREAM_FAILURE
}
