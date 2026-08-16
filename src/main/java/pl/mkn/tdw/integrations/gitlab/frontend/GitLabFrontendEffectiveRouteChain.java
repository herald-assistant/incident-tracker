package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendEffectiveRouteChain(
        GitLabFrontendScreenIdentity screen,
        List<GitLabFrontendRouteChainSegment> segments,
        List<String> routeParameters
) {

    public GitLabFrontendEffectiveRouteChain {
        screen = Objects.requireNonNull(screen, "screen must not be null");
        segments = segments != null ? List.copyOf(segments) : List.of();
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("effective route chain requires at least one segment");
        }
        if (!screen.routeNodeId().equals(segments.get(segments.size() - 1).nodeId())) {
            throw new IllegalArgumentException("effective route chain must end at the screen route node");
        }
    }
}
