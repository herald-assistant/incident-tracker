package pl.mkn.tdw.integrations.gitlab.frontend;

public enum GitLabFrontendReachabilityDependencyKind {
    FACADE,
    SERVICE,
    BACKEND_CLIENT,
    NGRX,
    RXJS,
    WEBSOCKET,
    IMPORTED_FUNCTION,
    EXTERNAL,
    UNKNOWN
}
