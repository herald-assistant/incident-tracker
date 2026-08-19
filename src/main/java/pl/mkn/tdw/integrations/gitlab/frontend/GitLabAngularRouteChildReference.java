package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabAngularRouteChildReference(
        String sliceRef,
        String nodeId,
        String screenId,
        String routePattern,
        String label,
        GitLabFrontendRouteNodeKind kind,
        GitLabFrontendDiscoveryStatus status,
        GitLabFrontendRouteTarget viewTarget,
        GitLabFrontendRouteTarget lazyTarget,
        String redirectTarget,
        boolean structural,
        boolean samePathAsParent,
        boolean hasChildren
) {
}
