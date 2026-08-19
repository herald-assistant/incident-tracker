package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabAngularRouteChildReference(
        String sliceRef,
        String nodeId,
        String screenId,
        String routePattern,
        String label,
        GitLabFrontendRouteTarget viewTarget,
        GitLabFrontendRouteTarget lazyTarget
) {
}
