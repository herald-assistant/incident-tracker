package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendRouteNode(
        String nodeId,
        String parentNodeId,
        GitLabFrontendScreenIdentity screen,
        String label,
        String pathSegment,
        String routePattern,
        String outlet,
        GitLabFrontendRouteNodeKind kind,
        GitLabFrontendDiscoveryStatus status,
        boolean lazyBoundary,
        List<String> routeParameters,
        GitLabFrontendRouteTarget viewTarget,
        GitLabFrontendRouteTarget lazyTarget,
        String redirectTarget,
        List<GitLabFrontendRouteConfiguration> configuration,
        GitLabFrontendSourceReference routeSource,
        List<String> limitations
) {

    public GitLabFrontendRouteNode {
        nodeId = required(nodeId, "nodeId");
        parentNodeId = normalize(parentNodeId);
        label = normalize(label);
        pathSegment = pathSegment != null ? pathSegment.trim() : null;
        routePattern = required(routePattern, "routePattern");
        outlet = StringUtils.hasText(outlet) ? outlet.trim() : "primary";
        kind = Objects.requireNonNull(kind, "kind must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        redirectTarget = normalize(redirectTarget);
        configuration = configuration != null ? List.copyOf(configuration) : List.of();
        routeSource = Objects.requireNonNull(routeSource, "routeSource must not be null");
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        if (kind == GitLabFrontendRouteNodeKind.SCREEN && screen == null) {
            throw new IllegalArgumentException("screen route node requires screen identity");
        }
        if (screen != null && !nodeId.equals(screen.routeNodeId())) {
            throw new IllegalArgumentException("screen routeNodeId must match nodeId");
        }
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
