package pl.mkn.tdw.integrations.gitlab.frontend;

public enum GitLabFrontendReachabilityEdgeKind {
    TEMPLATE_CHILD,
    ROUTED_CHILD,
    DYNAMIC_COMPONENT,
    COMPONENT_REFERENCE,
    USES_DEPENDENCY,
    DEPENDENCY_CALL
}
