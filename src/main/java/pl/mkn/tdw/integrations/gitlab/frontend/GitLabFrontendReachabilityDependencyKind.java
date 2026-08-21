package pl.mkn.tdw.integrations.gitlab.frontend;

public enum GitLabFrontendReachabilityDependencyKind {
    FACADE,
    SERVICE,
    BACKEND_CLIENT,
    NGRX,
    RXJS,
    WEBSOCKET,
    INHERITED_TYPE,
    IMPORTED_FUNCTION,
    EXTERNAL,
    UNKNOWN
}
