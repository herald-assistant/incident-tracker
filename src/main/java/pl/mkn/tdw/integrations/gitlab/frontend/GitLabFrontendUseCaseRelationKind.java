package pl.mkn.tdw.integrations.gitlab.frontend;

public enum GitLabFrontendUseCaseRelationKind {
    ROUTE_TO_VIEW,
    ROUTED_CHILD,
    COMPONENT_TO_TEMPLATE,
    USES_IMPORTED_SYMBOL,
    TEMPLATE_TO_HANDLER,
    LOCAL_METHOD_CALL,
    FRONTIER_EXPANSION
}
