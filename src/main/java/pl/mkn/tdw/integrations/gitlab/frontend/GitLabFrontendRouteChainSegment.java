package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendRouteChainSegment(
        String nodeId,
        String pathSegment,
        String routePattern,
        String outlet,
        List<GitLabFrontendRouteConfiguration> configuration,
        GitLabFrontendSourceReference source
) {

    public GitLabFrontendRouteChainSegment {
        if (!StringUtils.hasText(nodeId)) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        nodeId = nodeId.trim();
        pathSegment = pathSegment != null ? pathSegment.trim() : null;
        if (!StringUtils.hasText(routePattern)) {
            throw new IllegalArgumentException("routePattern must not be blank");
        }
        routePattern = routePattern.trim();
        outlet = StringUtils.hasText(outlet) ? outlet.trim() : "primary";
        configuration = configuration != null ? List.copyOf(configuration) : List.of();
        source = Objects.requireNonNull(source, "source must not be null");
    }
}
